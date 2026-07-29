package com.dj1012h.researchpilot.literature.agent;

/** Safe categories for server-side rejection of a model refinement proposal. */
public enum PlanRefinementRejectionReason {
    REFINEMENT_LIMIT_REACHED,
    INVALID_MODEL_OUTPUT,
    EMPTY_SUGGESTION,
    TOO_MANY_KEYWORDS,
    KEYWORD_TOO_LONG,
    REASON_INVALID,
    REFINED_QUERY_TOO_LONG,
    REFINED_PLAN_VALIDATION_FAILED
}
