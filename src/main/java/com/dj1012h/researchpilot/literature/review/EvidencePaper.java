package com.dj1012h.researchpilot.literature.review;

import java.util.List;
import java.util.Objects;

/** Minimal, source-independent evidence projection for one formal paper. */
public record EvidencePaper(
        CitationId citationId,
        String normalizedDoi,
        String title,
        List<String> authorDisplayNames,
        Integer publicationYear,
        String venue,
        String abstractText
) {
    public EvidencePaper {
        citationId = Objects.requireNonNull(citationId, "citationId must not be null");
        normalizedDoi = requireText(normalizedDoi, "normalizedDoi");
        title = requireText(title, "title");
        authorDisplayNames = List.copyOf(Objects.requireNonNull(
                authorDisplayNames, "authorDisplayNames must not be null"));
        if (authorDisplayNames.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("authorDisplayNames must not contain blank values");
        }
        abstractText = requireText(abstractText, "abstractText");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
