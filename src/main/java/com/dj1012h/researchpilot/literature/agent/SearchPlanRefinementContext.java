package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;

import java.util.Objects;

/** Minimal state summary supplied to one post-decision refinement attempt. */
public record SearchPlanRefinementContext(
        ValidatedSearchPlanContext current,
        int refinementCount,
        int firstRoundCandidateCount,
        int firstRoundVerifiedCount,
        String failureSummary
) {
    public SearchPlanRefinementContext {
        current = Objects.requireNonNull(current, "current must not be null");
        if (refinementCount < 0
                || firstRoundCandidateCount < 0
                || firstRoundVerifiedCount < 0) {
            throw new IllegalArgumentException("refinement counters must not be negative");
        }
        if (firstRoundVerifiedCount > firstRoundCandidateCount) {
            throw new IllegalArgumentException(
                    "verified count must not exceed candidate count"
            );
        }
        failureSummary = normalizeSummary(failureSummary);
    }

    private static String normalizeSummary(String value) {
        if (value == null || value.isBlank()) {
            return "INSUFFICIENT_VERIFIED_RESULTS";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }
}
