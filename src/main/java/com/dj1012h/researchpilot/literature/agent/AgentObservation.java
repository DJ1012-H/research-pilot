package com.dj1012h.researchpilot.literature.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable diagnostic facts only; raw provider payloads, prompts, and secrets are excluded. */
public record AgentObservation(
        AgentAction action,
        AgentStage stageBefore,
        AgentStage stageAfter,
        boolean success,
        int candidateCount,
        int deduplicatedCount,
        int verifiedCount,
        int newUniqueCandidateCount,
        int crossrefCallsUsed,
        Duration elapsed,
        String summary,
        String failureCode,
        Instant observedAt
) {
    public AgentObservation {
        action = Objects.requireNonNull(action, "action must not be null");
        stageBefore = Objects.requireNonNull(stageBefore, "stageBefore must not be null");
        stageAfter = Objects.requireNonNull(stageAfter, "stageAfter must not be null");
        elapsed = Objects.requireNonNull(elapsed, "elapsed must not be null");
        summary = requireSummary(summary);
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (candidateCount < 0 || deduplicatedCount < 0 || verifiedCount < 0
                || newUniqueCandidateCount < 0 || crossrefCallsUsed < 0 || elapsed.isNegative()) {
            throw new IllegalArgumentException("observation counts and elapsed must not be negative");
        }
        if (success && failureCode != null) throw new IllegalArgumentException("successful observation has no failure code");
    }
    private static String requireSummary(String value) {
        Objects.requireNonNull(value, "summary must not be null");
        if (value.isBlank() || value.length() > 500) throw new IllegalArgumentException("summary must contain at most 500 characters");
        return value;
    }
}
