package com.dj1012h.researchpilot.literature.rag.retrieval;

import java.util.List;

/** Internal trusted retrieval outcome shared by diagnostics and answer paths. */
public record TrustedRagRetrieval(
        String status,
        String activeEmbeddingVersion,
        int requestedTopK,
        int qdrantCandidateCount,
        int uniquePaperCandidateCount,
        int admittedPaperCount,
        int filteredCount,
        long elapsedMs,
        List<TrustedRagEvidence> evidence,
        String failureCode
) {
    public TrustedRagRetrieval {
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        if (requestedTopK < 0 || qdrantCandidateCount < 0 || uniquePaperCandidateCount < 0
                || admittedPaperCount < 0 || filteredCount < 0 || elapsedMs < 0) {
            throw new IllegalArgumentException("retrieval counters must be non-negative");
        }
    }

    public boolean failed() {
        return "FAILED".equals(status);
    }
}
