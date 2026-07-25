package com.dj1012h.researchpilot.literature.model;

import java.util.List;
import java.util.Objects;

/** Field-level evidence scaffold; it does not decide a final verification status. */
public record VerificationEvidence(
        String candidateId,
        String evidenceSource,
        List<FieldVerificationEvidence> fieldEvidence
) {

    public VerificationEvidence {
        candidateId = requireText(candidateId, "candidateId");
        evidenceSource = requireText(evidenceSource, "evidenceSource");
        fieldEvidence = List.copyOf(Objects.requireNonNull(fieldEvidence,
                "fieldEvidence must not be null"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
