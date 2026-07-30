package com.dj1012h.researchpilot.literature.persistence.entity;

public record LiteratureVerificationEvidenceEntity(
        long searchTaskId, Long paperId, String candidateFingerprint, String verificationStatus,
        String verificationSource, String referenceDoi, Double evidenceScore,
        String verificationRuleVersion, String reasonCodesCanonical
) { }
