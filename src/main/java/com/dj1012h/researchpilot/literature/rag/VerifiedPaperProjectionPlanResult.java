package com.dj1012h.researchpilot.literature.rag;

import java.util.Objects;

/** Explicit pre-embedding admission result used by idempotent index rebuilds. */
public record VerifiedPaperProjectionPlanResult(
        ProjectionRejectionReason rejectionReason,
        VerifiedPaperProjectionPlan plan
) {

    public VerifiedPaperProjectionPlanResult {
        if ((rejectionReason == null) == (plan == null)) {
            throw new IllegalArgumentException("admitted results require a plan and rejected results require a reason");
        }
    }

    public static VerifiedPaperProjectionPlanResult admitted(VerifiedPaperProjectionPlan plan) {
        return new VerifiedPaperProjectionPlanResult(null, Objects.requireNonNull(plan, "plan must not be null"));
    }

    public static VerifiedPaperProjectionPlanResult rejected(ProjectionRejectionReason reason) {
        return new VerifiedPaperProjectionPlanResult(
                Objects.requireNonNull(reason, "reason must not be null"),
                null);
    }

    public boolean admitted() {
        return rejectionReason == null;
    }
}
