package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;

/** Java-owned terminal outcome when policy and budget leave no action to propose. */
public class SearchActionDecisionUnavailableException extends RuntimeException {

    private final TerminationReason terminationReason;

    public SearchActionDecisionUnavailableException(TerminationReason terminationReason, String detail) {
        super(Objects.requireNonNull(detail, "detail must not be null"));
        this.terminationReason = Objects.requireNonNull(terminationReason, "terminationReason must not be null");
    }

    public TerminationReason getTerminationReason() { return terminationReason; }
}
