package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefAuthor;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefDate;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkMessage;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministically maps a Crossref work DTO to the provider-independent metadata contract.
 * It performs no network, persistence, clock, or verification operation. The Crossref URL is
 * intentionally not mapped because {@link CrossrefWorkMetadata} has no compatible URL field.
 */
@Component
public class CrossrefPaperMapper {

    private static final Pattern ABSTRACT_MARKUP = Pattern.compile("(?s)<[^>]*>");

    private final DoiNormalizer doiNormalizer;

    public CrossrefPaperMapper(DoiNormalizer doiNormalizer) {
        this.doiNormalizer = doiNormalizer;
    }

    public CrossrefWorkMetadata map(CrossrefWorkMessage message) {
        if (message == null) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE,
                    "Crossref response contains an invalid work item");
        }
        String normalizedDoi = doiNormalizer.normalize(message.doi());
        if (normalizedDoi == null) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE,
                    "Crossref response contains an invalid DOI");
        }
        return new CrossrefWorkMetadata(
                normalizedDoi,
                firstText(message.title()),
                authorNames(message.author()),
                publicationYear(message),
                firstText(message.containerTitle()),
                textOrNull(message.type()),
                textOrNull(message.publisher()),
                normalizeAbstract(message.abstractText())
        );
    }

    private List<String> authorNames(List<CrossrefAuthor> authors) {
        if (authors == null) return List.of();
        List<String> names = new ArrayList<>();
        for (CrossrefAuthor author : authors) {
            if (author == null) continue;
            String name = textOrNull(author.name());
            if (name == null) name = join(author.given(), author.family());
            if (name != null) names.add(name);
        }
        return List.copyOf(names);
    }

    private Integer publicationYear(CrossrefWorkMessage message) {
        CrossrefDate[] dates = {
                message.publishedPrint(), message.publishedOnline(), message.issued(), message.created()
        };
        for (CrossrefDate date : dates) {
            Integer year = firstYear(date);
            if (year != null) return year;
        }
        return null;
    }

    private Integer firstYear(CrossrefDate date) {
        if (date == null || date.dateParts() == null || date.dateParts().isEmpty()) return null;
        List<Integer> parts = date.dateParts().getFirst();
        return parts == null || parts.isEmpty() ? null : parts.getFirst();
    }

    private String firstText(List<String> values) {
        if (values == null) return null;
        return values.stream().map(this::textOrNull).filter(value -> value != null).findFirst().orElse(null);
    }

    private String join(String given, String family) {
        String left = textOrNull(given);
        String right = textOrNull(family);
        if (left == null) return right;
        if (right == null) return left;
        return left + " " + right;
    }

    private String textOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeAbstract(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = ABSTRACT_MARKUP.matcher(value).replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
