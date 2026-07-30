package com.dj1012h.researchpilot.literature.persistence.entity;

public record LiteraturePlanAttemptEntity(
        long searchTaskId, int attemptNo, String attemptStatus, String planVersion, String promptVersion,
        String schemaVersion, String topic, String searchQuery, String keywordsCanonical,
        String languagesCanonical, String publicationTypesCanonical, String searchSort,
        int fromYear, int toYear, int candidateLimit, int resultLimit
) { }
