package com.dj1012h.researchpilot.literature.agent;

import java.time.Instant;
import java.util.Objects;

/** Result of exactly one attempted action. */
public record SearchActionExecutionResult(
        AgentExecutionContext context,
        AgentAction action,
        ActionDecisionSource decisionSource,
        AgentStage stageBefore,
        AgentStage stageAfter,
        ExecutionStepStatus status,
        String observationSummary,
        String failureCode,
        ActionCost actualCost,
        BudgetUsageSnapshot budgetBefore,
        BudgetUsageSnapshot budgetAfter,
        Instant startedAt,
        Instant finishedAt
) {
    public SearchActionExecutionResult {
        context = Objects.requireNonNull(context, "context must not be null");
        action = Objects.requireNonNull(action, "action must not be null");
        stageBefore = Objects.requireNonNull(stageBefore, "stageBefore must not be null");
        stageAfter = Objects.requireNonNull(stageAfter, "stageAfter must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        observationSummary = Objects.requireNonNull(observationSummary, "observationSummary must not be null");
        actualCost = Objects.requireNonNull(actualCost, "actualCost must not be null");
        budgetBefore = Objects.requireNonNull(budgetBefore, "budgetBefore must not be null");
        budgetAfter = Objects.requireNonNull(budgetAfter, "budgetAfter must not be null");
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt must not be null");
    }

    public ExecutionTraceDraft traceDraft() {
        long elapsedMs = Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        return new ExecutionTraceDraft(
                action, decisionSource, stageBefore, stageAfter, status, elapsedMs,
                budgetBefore, budgetAfter, observationSummary, failureCode,
                context.state().terminationReason(), startedAt, finishedAt);
    }
}
