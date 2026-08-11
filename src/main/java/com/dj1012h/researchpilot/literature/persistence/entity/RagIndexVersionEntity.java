package com.dj1012h.researchpilot.literature.persistence.entity;

import java.time.Instant;

public record RagIndexVersionEntity(
        String embeddingVersion,
        String collectionName,
        int vectorDimensions,
        String lastBuildStatus,
        boolean active,
        int sourcePaperCount,
        long pointCount,
        String lastFailureCode,
        Instant buildStartedAt,
        Instant buildCompletedAt,
        Instant activatedAt
) { }
