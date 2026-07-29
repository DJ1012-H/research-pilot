package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;

import java.util.Objects;

/** Internal context retained for provenance-preserving plan refinement. */
public record ValidatedSearchPlanContext(
        SearchPlanGenerationContext generationContext,
        SearchPlanValidationResult validationResult
) {
    public ValidatedSearchPlanContext {
        generationContext = Objects.requireNonNull(
                generationContext,
                "generationContext must not be null"
        );
        validationResult = Objects.requireNonNull(
                validationResult,
                "validationResult must not be null"
        );
    }
}
