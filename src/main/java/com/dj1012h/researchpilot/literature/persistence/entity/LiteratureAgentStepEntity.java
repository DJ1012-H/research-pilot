package com.dj1012h.researchpilot.literature.persistence.entity;

import java.time.Instant;

public record LiteratureAgentStepEntity(
        long searchTaskId, String traceId, int stepIndex, String action, String decisionSource,
        String stageBefore, String stageAfter, String stepStatus, long elapsedMs,
        int searchRoundCountBefore, int searchRoundCountAfter, int planAdjustmentCountBefore,
        int planAdjustmentCountAfter, int businessStepCountBefore, int businessStepCountAfter,
        int uniqueCandidateCountBefore, int uniqueCandidateCountAfter, int crossrefCallCountBefore,
        int crossrefCallCountAfter, boolean deadlineExceededBefore, boolean deadlineExceededAfter,
        String observationSummary, String failureCode, String terminationReason, Instant startedAt, Instant finishedAt
) { }
