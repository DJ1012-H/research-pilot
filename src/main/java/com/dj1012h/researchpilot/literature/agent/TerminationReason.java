package com.dj1012h.researchpilot.literature.agent;

/** Task-level completion or termination reason; this never replaces VerificationStatus. */
public enum TerminationReason {
    TARGET_REACHED,
    PARTIAL_RESULTS,
    NO_VERIFIED_RESULTS,
    SEARCH_ROUND_LIMIT_REACHED,
    PLAN_ADJUSTMENT_LIMIT_REACHED,
    STEP_LIMIT_REACHED,
    CANDIDATE_BUDGET_EXHAUSTED,
    CROSSREF_BUDGET_EXHAUSTED,
    DEADLINE_EXCEEDED,
    INVALID_STATE,
    EXTERNAL_SERVICE_UNAVAILABLE,
    UNEXPECTED_FAILURE
}
