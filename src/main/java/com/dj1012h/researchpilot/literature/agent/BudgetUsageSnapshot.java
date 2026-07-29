package com.dj1012h.researchpilot.literature.agent;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Safe, immutable projection of budget usage at one execution step boundary. */
public record BudgetUsageSnapshot(
        int searchRoundCount,
        int planAdjustmentCount,
        int businessStepCount,
        int uniqueCandidateCount,
        int crossrefCallCount,
        boolean deadlineExceeded
) {
    public BudgetUsageSnapshot {
        if (searchRoundCount < 0 || planAdjustmentCount < 0 || businessStepCount < 0
                || uniqueCandidateCount < 0 || crossrefCallCount < 0) {
            throw new IllegalArgumentException("budget usage must not be negative");
        }
    }

    public static BudgetUsageSnapshot from(AgentState state, Clock clock) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        return new BudgetUsageSnapshot(
                state.searchRoundCount(),
                state.planAdjustmentCount(),
                state.businessStepCount(),
                state.uniqueCandidateCount(),
                state.crossrefCallCount(),
                !Instant.now(clock).isBefore(state.deadline())
        );
    }
}
