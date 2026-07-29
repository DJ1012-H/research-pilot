package com.dj1012h.researchpilot.literature.agent;

import java.time.Instant;
import java.util.Objects;

/** Trace payload before the recorder assigns a per-trace step index. */
public record ExecutionTraceDraft(
        AgentAction action,
        ActionDecisionSource decisionSource,
        AgentStage stageBefore,
        AgentStage stageAfter,
        ExecutionStepStatus status,
        long elapsedMs,
        BudgetUsageSnapshot budgetBefore,
        BudgetUsageSnapshot budgetAfter,
        String observationSummary,
        String failureCode,
        TerminationReason terminationReason,
        Instant startedAt,
        Instant finishedAt
) {
    public ExecutionTraceDraft {
        action = Objects.requireNonNull(action, "action must not be null");
        stageBefore = Objects.requireNonNull(stageBefore, "stageBefore must not be null");
        stageAfter = Objects.requireNonNull(stageAfter, "stageAfter must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        budgetBefore = Objects.requireNonNull(budgetBefore, "budgetBefore must not be null");
        budgetAfter = Objects.requireNonNull(budgetAfter, "budgetAfter must not be null");
        observationSummary = safeSummary(observationSummary);
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        if (elapsedMs < 0) throw new IllegalArgumentException("elapsedMs must not be negative");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
    }

    private static String safeSummary(String value) {
        Objects.requireNonNull(value, "observationSummary must not be null");
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) throw new IllegalArgumentException("observationSummary must not be blank");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
