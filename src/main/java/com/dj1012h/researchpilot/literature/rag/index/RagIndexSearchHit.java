package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.rag.RagPointPayload;

import java.util.Objects;

/** A candidate returned by a derived index; it is not trusted paper evidence. */
public record RagIndexSearchHit(RagPointPayload payload, double score) {

    public RagIndexSearchHit {
        payload = Objects.requireNonNull(payload, "payload must not be null");
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
    }
}
