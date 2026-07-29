package com.dj1012h.researchpilot.literature.api.dto;

import java.util.List;
import java.util.Objects;

/** Public bibliographic data for one citation used by the generated review. */
public record ReviewCitation(
        String citationId,
        int formalPaperPosition,
        String doi,
        String title,
        List<String> authors,
        Integer publicationYear,
        String venue
) {
    public ReviewCitation {
        citationId = requireText(citationId, "citationId");
        doi = requireText(doi, "doi");
        title = requireText(title, "title");
        if (formalPaperPosition < 1 || !citationId.equals("P" + formalPaperPosition)) {
            throw new IllegalArgumentException("citationId must match formalPaperPosition");
        }
        authors = List.copyOf(Objects.requireNonNull(authors, "authors must not be null"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
