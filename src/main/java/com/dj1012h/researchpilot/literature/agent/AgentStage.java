package com.dj1012h.researchpilot.literature.agent;

/** Finite, observable stages of the controlled research workflow. */
public enum AgentStage {
    INITIALIZED,
    PLAN_READY,
    SEARCHING,
    CANDIDATES_RETRIEVED,
    DEDUPLICATING,
    CANDIDATES_DEDUPLICATED,
    VERIFYING,
    VERIFICATION_COMPLETED,
    EVALUATING_RESULTS,
    COMPLETED,
    TERMINATED
}
