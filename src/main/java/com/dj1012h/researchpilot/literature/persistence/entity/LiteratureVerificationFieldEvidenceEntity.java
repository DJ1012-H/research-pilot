package com.dj1012h.researchpilot.literature.persistence.entity;

public record LiteratureVerificationFieldEvidenceEntity(
        long verificationEvidenceId, int fieldOrdinal, String fieldName, String matchStatus,
        String candidateNormalizedValue, String referenceNormalizedValue, Double similarityScore,
        String reasonCode
) { }
