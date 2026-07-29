package com.dj1012h.researchpilot.literature.model;

/** Fields whose provenance is retained beside a trusted search plan. */
public enum SearchConstraintField {
    ORIGINAL_QUERY,
    TOPIC,
    ENGLISH_KEYWORDS,
    SEARCH_QUERY,
    FROM_YEAR,
    TO_YEAR,
    LANGUAGES,
    PUBLICATION_TYPES,
    SORT,
    RESULT_LIMIT,
    CANDIDATE_LIMIT,
    MAX_SEARCH_ROUNDS,
    MAX_PLAN_ADJUSTMENTS,
    MAX_BUSINESS_STEPS,
    MAX_UNIQUE_CANDIDATES,
    MAX_CROSSREF_CALLS,
    TOTAL_TIMEOUT
}
