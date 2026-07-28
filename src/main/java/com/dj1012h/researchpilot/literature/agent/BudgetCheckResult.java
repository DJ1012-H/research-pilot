package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;

/** Explainable non-throwing result of the single pre-action budget policy. */
public record BudgetCheckResult(boolean allowed, TerminationReason reason, String detail) {
    public BudgetCheckResult {
        detail = Objects.requireNonNull(detail, "detail must not be null");
        if (allowed && reason != null) throw new IllegalArgumentException("allowed result must not have a reason");
        if (!allowed && reason == null) throw new IllegalArgumentException("denied result must have a reason");
    }
    public static BudgetCheckResult permitted() { return new BudgetCheckResult(true, null, "allowed"); }
    public static BudgetCheckResult denied(TerminationReason reason, String detail) {
        return new BudgetCheckResult(false, Objects.requireNonNull(reason, "reason must not be null"), detail);
    }
}
