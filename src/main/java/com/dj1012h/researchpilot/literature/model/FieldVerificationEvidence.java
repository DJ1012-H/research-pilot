package com.dj1012h.researchpilot.literature.model;

import java.util.Objects;

public record FieldVerificationEvidence(
        VerificationField field,
        String candidateNormalizedValue,
        String evidenceNormalizedValue,
        FieldMatchStatus status,
        Double score,
        String explanation
) {

    public FieldVerificationEvidence {
        field = Objects.requireNonNull(field, "field must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (score != null && (score.isNaN() || score.isInfinite() || score < 0.0 || score > 1.0)) {
            throw new IllegalArgumentException("score must be null or between 0 and 1");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }
}
