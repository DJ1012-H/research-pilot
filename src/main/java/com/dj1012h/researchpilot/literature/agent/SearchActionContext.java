package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;
import java.util.Set;

/** Minimal Java-produced state snapshot safe to disclose to the action model. */
public record SearchActionContext(
        AgentStage currentStage,
        int requestedCount,
        int verifiedCount,
        int retrievedCandidateCount,
        int deduplicatedCandidateCount,
        int searchRoundCount,
        int remainingSearchRounds,
        int planAdjustmentCount,
        int remainingPlanAdjustments,
        int businessStepCount,
        int remainingBusinessSteps,
        int crossrefCallCount,
        int remainingCrossrefCalls,
        boolean deadlineExceeded,
        Set<AgentAction> allowedActions,
        String recentObservation
) {
    public SearchActionContext {
        currentStage = Objects.requireNonNull(currentStage, "currentStage must not be null");
        allowedActions = Set.copyOf(Objects.requireNonNull(allowedActions, "allowedActions must not be null"));
        if (requestedCount < 1 || verifiedCount < 0 || retrievedCandidateCount < 0 || deduplicatedCandidateCount < 0
                || searchRoundCount < 0 || remainingSearchRounds < 0 || planAdjustmentCount < 0
                || remainingPlanAdjustments < 0 || businessStepCount < 0 || remainingBusinessSteps < 0
                || crossrefCallCount < 0 || remainingCrossrefCalls < 0) {
            throw new IllegalArgumentException("search action context counts must not be negative");
        }
        if (recentObservation != null && recentObservation.length() > 200) {
            throw new IllegalArgumentException("recentObservation must not exceed 200 characters");
        }
    }

    public String prompt() {
        return """
                currentStage: %s
                requestedCount: %d
                verifiedCount: %d
                retrievedCandidateCount: %d
                deduplicatedCandidateCount: %d
                searchRoundCount: %d
                remainingSearchRounds: %d
                planAdjustmentCount: %d
                remainingPlanAdjustments: %d
                businessStepCount: %d
                remainingBusinessSteps: %d
                crossrefCallCount: %d
                remainingCrossrefCalls: %d
                deadlineExceeded: %s
                allowedActions: %s
                recentObservation: %s
                """.formatted(currentStage, requestedCount, verifiedCount, retrievedCandidateCount,
                deduplicatedCandidateCount, searchRoundCount, remainingSearchRounds, planAdjustmentCount,
                remainingPlanAdjustments, businessStepCount, remainingBusinessSteps, crossrefCallCount,
                remainingCrossrefCalls, deadlineExceeded, allowedActions, recentObservation == null ? "none" : recentObservation);
    }
}
