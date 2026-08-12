package com.dj1012h.researchpilot.literature.rag.answer;

/** Retrieval observations; score is relatedness only, never a trust probability. */
public record RagAnswerRetrievalSummary(
        String activeEmbeddingVersion,
        int requestedTopK,
        int qdrantCandidateCount,
        int uniquePaperCandidateCount,
        int admittedPaperCount,
        int evidenceCount,
        int filteredCount,
        Double lowestAdmittedScore
) {
    public RagAnswerRetrievalSummary {
        if (requestedTopK < 0 || qdrantCandidateCount < 0 || uniquePaperCandidateCount < 0
                || admittedPaperCount < 0 || evidenceCount < 0 || filteredCount < 0) {
            throw new IllegalArgumentException("retrieval summary counts must not be negative");
        }
        if (lowestAdmittedScore != null && !Double.isFinite(lowestAdmittedScore)) {
            throw new IllegalArgumentException("lowestAdmittedScore must be finite");
        }
    }
}
