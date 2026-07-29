package com.dj1012h.researchpilot.literature.agent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Audit/debug projection only; it is never used as Agent decision state. */
public record ExecutionTraceEntry(
        UUID traceId,
        int stepIndex,
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
    public ExecutionTraceEntry {
        traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        if (stepIndex < 0) throw new IllegalArgumentException("stepIndex must not be negative");
        action = Objects.requireNonNull(action, "action must not be null");
        stageBefore = Objects.requireNonNull(stageBefore, "stageBefore must not be null");
        stageAfter = Objects.requireNonNull(stageAfter, "stageAfter must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        budgetBefore = Objects.requireNonNull(budgetBefore, "budgetBefore must not be null");
        budgetAfter = Objects.requireNonNull(budgetAfter, "budgetAfter must not be null");
        observationSummary = Objects.requireNonNull(observationSummary, "observationSummary must not be null");
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt must not be null");
    }
}
