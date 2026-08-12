package com.dj1012h.researchpilot.literature.rag.retrieval;

import java.util.Objects;

/** Normalized, bounded provider-neutral retrieval query. */
public record RagSearchQuery(String query, int topK, RagSearchFilter filter) {
    public RagSearchQuery {
        query = Objects.requireNonNull(query, "query must not be null");
        if (query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        if (topK < 1) throw new IllegalArgumentException("topK must be positive");
        filter = Objects.requireNonNull(filter, "filter must not be null");
    }
}
