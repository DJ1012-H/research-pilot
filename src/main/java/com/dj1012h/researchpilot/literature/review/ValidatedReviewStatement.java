package com.dj1012h.researchpilot.literature.review;

import java.util.List;
import java.util.Objects;

public record ValidatedReviewStatement(
        ReviewStatementType type,
        String text,
        List<CitationId> citationIds
) {
    public ValidatedReviewStatement {
        type = Objects.requireNonNull(type, "type must not be null");
        text = Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        citationIds = List.copyOf(Objects.requireNonNull(citationIds, "citationIds must not be null"));
        if (citationIds.isEmpty()) {
            throw new IllegalArgumentException("citationIds must not be empty");
        }
    }
}
