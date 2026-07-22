package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefAuthor;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefDate;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkMessage;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** Maps external Crossref DTOs to the internal search-port model only. */
@Component
public class CrossrefSearchAdapter implements CrossrefSearchPort {

    private final CrossrefClient client;
    private final DoiNormalizer doiNormalizer;

    public CrossrefSearchAdapter(CrossrefClient client, DoiNormalizer doiNormalizer) {
        this.client = client;
        this.doiNormalizer = doiNormalizer;
    }

    @Override
    public CrossrefLookupResult findByDoi(String doi) {
        try {
            return CrossrefLookupResult.found(map(client.getWorkByDoi(doi).message()));
        } catch (CrossrefApiException exception) {
            if (exception.getFailureType() == CrossrefFailureType.NOT_FOUND) {
                return CrossrefLookupResult.notFound();
            }
            throw exception;
        }
    }

    private CrossrefWorkMetadata map(CrossrefWorkMessage message) {
        String normalizedDoi = doiNormalizer.normalize(message.doi());
        if (normalizedDoi == null) {
            throw new CrossrefApiException(
                    CrossrefFailureType.INVALID_RESPONSE,
                    "Crossref 响应包含无效 DOI"
            );
        }
        return new CrossrefWorkMetadata(
                normalizedDoi,
                firstText(message.title()),
                authorNames(message.author()),
                publicationYear(message),
                firstText(message.containerTitle()),
                textOrNull(message.type()),
                textOrNull(message.publisher())
        );
    }

    private List<String> authorNames(List<CrossrefAuthor> authors) {
        if (authors == null) return List.of();
        List<String> names = new ArrayList<>();
        for (CrossrefAuthor author : authors) {
            if (author == null) continue;
            String name = textOrNull(author.name());
            if (name == null) {
                name = join(author.given(), author.family());
            }
            if (name != null) names.add(name);
        }
        return names;
    }

    private Integer publicationYear(CrossrefWorkMessage message) {
        CrossrefDate[] dates = {
                message.publishedOnline(), message.publishedPrint(), message.published(), message.issued()
        };
        for (CrossrefDate date : dates) {
            if (date == null || date.dateParts() == null || date.dateParts().isEmpty()) continue;
            List<Integer> parts = date.dateParts().getFirst();
            if (parts != null && !parts.isEmpty() && parts.getFirst() != null) return parts.getFirst();
        }
        return null;
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

    private String textOrNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
