package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;

/** Model proposed a refinement, but the server refused to adopt it. */
public class PlanRefinementRejectedException extends RuntimeException {

    private final PlanRefinementRejectionReason reason;

    public PlanRefinementRejectedException(PlanRefinementRejectionReason reason) {
        this(reason, null);
    }

    public PlanRefinementRejectedException(
            PlanRefinementRejectionReason reason,
            Throwable cause
    ) {
        super(message(reason), cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public PlanRefinementRejectionReason getReason() {
        return reason;
    }

    private static String message(PlanRefinementRejectionReason reason) {
        return "Search plan refinement rejected: "
                + Objects.requireNonNull(reason, "reason must not be null");
    }
}
