package com.dj1012h.researchpilot.literature.rag.index;

import java.time.Instant;
import java.util.Objects;

public record RagIndexVersionState(
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
) {

    public RagIndexVersionState {
        embeddingVersion = requireText(embeddingVersion, "embeddingVersion");
        collectionName = requireText(collectionName, "collectionName");
        if (vectorDimensions < 1) throw new IllegalArgumentException("vectorDimensions must be positive");
        lastBuildStatus = requireText(lastBuildStatus, "lastBuildStatus");
        if (sourcePaperCount < 0 || pointCount < 0) {
            throw new IllegalArgumentException("index counts must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
