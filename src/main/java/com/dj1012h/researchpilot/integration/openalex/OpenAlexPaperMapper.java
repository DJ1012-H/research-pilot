package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexAuthorshipDTO;
import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexLocationDTO;
import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorkDTO;
import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
public class OpenAlexPaperMapper {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern OPENALEX_PREFIX =
            Pattern.compile("(?i)^https?://openalex\\.org/");
    private static final Pattern ORCID_PREFIX =
            Pattern.compile("(?i)^https?://orcid\\.org/");

    private final DoiNormalizer doiNormalizer;

    public OpenAlexPaperMapper(DoiNormalizer doiNormalizer) {
        this.doiNormalizer = doiNormalizer;
    }

    public List<CandidatePaper> map(OpenAlexWorksResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .filter(work -> work != null)
                .map(this::map)
                .toList();
    }

    public CandidatePaper map(OpenAlexWorkDTO work) {
        String normalizedDoi = doiNormalizer.normalize(work.doi());
        LocalDate publicationDate = parseDate(work.publicationDate());
        Integer publicationYear = work.publicationYear() != null
                ? work.publicationYear()
                : publicationDate == null ? null : publicationDate.getYear();
        OpenAlexLocationDTO preferredLocation = work.bestOpenAccessLocation();
        OpenAlexLocationDTO fallbackLocation = work.primaryLocation();

        String landingPageUrl = firstNonBlank(
                locationLandingPage(preferredLocation),
                locationLandingPage(fallbackLocation),
                doiLandingPage(normalizedDoi)
        );
        String pdfUrl = firstNonBlank(
                locationPdf(preferredLocation),
                locationPdf(fallbackLocation)
        );

        return new CandidatePaper(
                //返回candidatepaper列表
                normalizeIdentifier(work.id(), OPENALEX_PREFIX),
                normalizedDoi,
                normalizeWhitespace(work.title()),
                mapAuthors(work.authorships()),
                sourceName(work.primaryLocation(), work.bestOpenAccessLocation()),
                publicationDate,
                publicationYear,
                normalizeWhitespace(work.type()),
                normalizeWhitespace(work.language()),
                work.citedByCount() == null ? 0 : Math.max(0, work.citedByCount()),
                restoreAbstract(work.abstractInvertedIndex()),
                landingPageUrl,
                pdfUrl,
                isOpenAccess(preferredLocation, fallbackLocation, pdfUrl),
                CandidatePaper.CandidateSource.OPENALEX

        );
    }

    String restoreAbstract(Map<String, List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) {
            return null;
        }

        TreeMap<Integer, String> wordsByPosition = new TreeMap<>();
        invertedIndex.forEach((word, positions) -> {
            String normalizedWord = normalizeWhitespace(word);
            if (normalizedWord == null || positions == null) {
                return;
            }
            positions.stream()
                    .filter(position -> position != null && position >= 0)
                    .forEach(position -> wordsByPosition.putIfAbsent(position, normalizedWord));
        });

        return wordsByPosition.isEmpty()
                ? null
                : String.join(" ", wordsByPosition.values());
    }

    private List<CandidatePaper.Author> mapAuthors(List<OpenAlexAuthorshipDTO> authorships) {
        if (authorships == null || authorships.isEmpty()) {
            return List.of();
        }

        List<CandidatePaper.Author> authors = new ArrayList<>();
        for (OpenAlexAuthorshipDTO authorship : authorships) {
            if (authorship == null || authorship.author() == null) {
                continue;
            }
            OpenAlexAuthorshipDTO.Author author = authorship.author();
            String displayName = normalizeWhitespace(author.displayName());
            if (displayName == null) {
                continue;
            }
            authors.add(new CandidatePaper.Author(
                    normalizeIdentifier(author.id(), OPENALEX_PREFIX),
                    displayName,
                    normalizeIdentifier(author.orcid(), ORCID_PREFIX)
            ));
        }
        return List.copyOf(authors);
    }

    private String sourceName(OpenAlexLocationDTO primary, OpenAlexLocationDTO bestOpenAccess) {
        String primaryName = primary == null || primary.source() == null
                ? null
                : primary.source().displayName();
        String openAccessName = bestOpenAccess == null || bestOpenAccess.source() == null
                ? null
                : bestOpenAccess.source().displayName();
        return normalizeWhitespace(firstNonBlank(primaryName, openAccessName));
    }

    private boolean isOpenAccess(OpenAlexLocationDTO preferred,
                                 OpenAlexLocationDTO fallback,
                                 String pdfUrl) {
        return Boolean.TRUE.equals(preferred == null ? null : preferred.openAccess())
                || Boolean.TRUE.equals(fallback == null ? null : fallback.openAccess())
                || pdfUrl != null;
    }

    private String locationLandingPage(OpenAlexLocationDTO location) {
        return location == null ? null : location.landingPageUrl();
    }

    private String locationPdf(OpenAlexLocationDTO location) {
        return location == null ? null : location.pdfUrl();
    }

    private String doiLandingPage(String normalizedDoi) {
        return normalizedDoi == null ? null : "https://doi.org/" + normalizedDoi;
    }

    private String normalizeIdentifier(String value, Pattern prefix) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimToNull(prefix.matcher(trimmed).replaceFirst(""));
    }

    private String normalizeWhitespace(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : WHITESPACE.matcher(trimmed).replaceAll(" ");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LocalDate parseDate(String value) {
        String date = trimToNull(value);
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
