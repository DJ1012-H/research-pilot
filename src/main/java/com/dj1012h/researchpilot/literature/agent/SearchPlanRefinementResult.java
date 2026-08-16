package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.model.SearchConstraintOrigins;
import com.dj1012h.researchpilot.literature.model.SearchPlan;

import java.util.Objects;

/** Trusted output of exactly one controlled plan-refinement attempt. */
public record SearchPlanRefinementResult(
        SearchPlan refinedPlan,
        SearchConstraintOrigins origins,
        SearchPlanDiff diff,
        int refinementAttempt
) {
    public SearchPlanRefinementResult {
        refinedPlan = Objects.requireNonNull(refinedPlan, "refinedPlan must not be null");
        origins = Objects.requireNonNull(origins, "origins must not be null");
        diff = Objects.requireNonNull(diff, "diff must not be null");
        if (refinementAttempt < 1) {
            throw new IllegalArgumentException("refinementAttempt must be positive");
        }
    }
}
