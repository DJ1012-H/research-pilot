package com.dj1012h.researchpilot.literature.model;

import java.util.Objects;

/** Trusted plan plus the provenance captured while resolving its final values. */
public record SearchPlanValidationResult(
        SearchPlan plan,
        SearchConstraintOrigins origins
) {
    public SearchPlanValidationResult {
        plan = Objects.requireNonNull(plan, "plan must not be null");
        origins = Objects.requireNonNull(origins, "origins must not be null");
    }
}
