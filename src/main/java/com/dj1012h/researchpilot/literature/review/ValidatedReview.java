package com.dj1012h.researchpilot.literature.review;

import java.util.List;
import java.util.Objects;

/** Structurally and citation-mapping validated abstract-level review. */
public record ValidatedReview(List<ValidatedReviewStatement> statements) {
    public ValidatedReview {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements must not be null"));
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("statements must not be empty");
        }
    }
}
