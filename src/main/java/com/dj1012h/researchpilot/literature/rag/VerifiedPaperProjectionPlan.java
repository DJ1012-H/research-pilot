package com.dj1012h.researchpilot.literature.rag;

import java.util.List;
import java.util.Objects;

/** Admission-approved point payloads that have not yet invoked the embedding provider. */
public record VerifiedPaperProjectionPlan(List<RagPointPayload> points) {

    public VerifiedPaperProjectionPlan {
        points = List.copyOf(Objects.requireNonNull(points, "points must not be null"));
        if (points.isEmpty()) throw new IllegalArgumentException("points must not be empty");
        long paperId = points.getFirst().paperId();
        String embeddingVersion = points.getFirst().embeddingVersion();
        if (points.stream().anyMatch(point -> point.paperId() != paperId
                || !embeddingVersion.equals(point.embeddingVersion()))) {
            throw new IllegalArgumentException("all planned points must belong to one paper and embedding version");
        }
    }

    public long paperId() {
        return points.getFirst().paperId();
    }
}
