package com.dj1012h.researchpilot.literature.rag;

import java.util.List;
import java.util.Objects;

/** Explicit admission outcome; rejected inputs expose no projected segments. */
public record VerifiedPaperProjectionResult(
        ProjectionRejectionReason rejectionReason,
        List<VerifiedPaperProjection> projections
) {

    public VerifiedPaperProjectionResult {
        projections = List.copyOf(Objects.requireNonNull(projections, "projections must not be null"));
        if ((rejectionReason == null) == projections.isEmpty()) {
            throw new IllegalArgumentException(
                    "admitted results require projections and rejected results require a reason");
        }
    }

    public static VerifiedPaperProjectionResult admitted(List<VerifiedPaperProjection> projections) {
        return new VerifiedPaperProjectionResult(null, projections);
    }

    public static VerifiedPaperProjectionResult rejected(ProjectionRejectionReason reason) {
        return new VerifiedPaperProjectionResult(
                Objects.requireNonNull(reason, "reason must not be null"),
                List.of());
    }

    public boolean admitted() {
        return rejectionReason == null;
    }
}
