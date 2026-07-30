package com.dj1012h.researchpilot.literature.persistence.entity;

import java.time.Instant;

/** Database projection only; never used as Agent state. */
public record LiteratureSearchTaskEntity(
        String taskId, String taskStatus, String reviewStatus, String publicTerminationReason,
        int requestedCount, int candidateCount, int deduplicatedCount, int verifiedCount,
        int partiallyVerifiedCount, int unverifiedCount, int rejectedCount, int modelCallCount,
        int reviewModelCallCount, int reviewRepairCount, String queryHash, int queryLength,
        Instant startedAt, Instant completedAt
) { }
