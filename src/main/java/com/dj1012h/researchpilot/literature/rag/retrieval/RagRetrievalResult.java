package com.dj1012h.researchpilot.literature.rag.retrieval;

import java.util.List;

/** Bounded Day 4 retrieval response; it never contains query vectors. */
public record RagRetrievalResult(
        String status,
        String activeEmbeddingVersion,
        int requestedTopK,
        int qdrantCandidateCount,
        int uniquePaperCandidateCount,
        int admittedPaperCount,
        int filteredCount,
        long elapsedMs,
        List<RagSearchHit> results,
        RagRetrievalDiagnostics diagnostics
) {
    public RagRetrievalResult {
        results = List.copyOf(results == null ? List.of() : results);
    }
}
