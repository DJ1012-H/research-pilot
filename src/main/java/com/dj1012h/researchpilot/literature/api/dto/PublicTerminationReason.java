package com.dj1012h.researchpilot.literature.api.dto;

/** Coarse, stable public termination categories with no internal control detail. */
public enum PublicTerminationReason {
    TARGET_REACHED,
    PARTIAL_RESULTS,
    NO_VERIFIED_RESULTS,
    LIMIT_REACHED,
    DEADLINE_EXCEEDED,
    EXTERNAL_SERVICE_UNAVAILABLE,
    SAFELY_TERMINATED
}
