package com.dj1012h.researchpilot.literature.persistence.entity;

public record LiteraturePaperEntity(
        String normalizedDoi, String openalexId, String title, String authorsCanonical,
        Integer publicationYear, String venue, String publicationType, String language,
        String abstractText, int citedByCount, String source,
        String currentVerificationStatus, String verificationRuleVersion
) { }
