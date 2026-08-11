package com.dj1012h.researchpilot.literature.rag.index;

public record RagIndexRebuildResult(
        int sourcePaperCount,
        long expectedPointCount,
        long actualPointCount,
        int embeddedPaperCount,
        int skippedEmbeddingPaperCount,
        int payloadOnlyUpdateCount,
        int deletedPointCount
) {

    public RagIndexRebuildResult {
        if (sourcePaperCount < 0 || expectedPointCount < 0 || actualPointCount < 0
                || embeddedPaperCount < 0 || skippedEmbeddingPaperCount < 0
                || payloadOnlyUpdateCount < 0 || deletedPointCount < 0) {
            throw new IllegalArgumentException("rebuild counts must not be negative");
        }
        if (embeddedPaperCount + skippedEmbeddingPaperCount != sourcePaperCount) {
            throw new IllegalArgumentException("every source paper must be embedded or safely skipped");
        }
    }
}
