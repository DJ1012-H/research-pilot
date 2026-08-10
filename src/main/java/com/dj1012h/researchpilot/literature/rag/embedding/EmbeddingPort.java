package com.dj1012h.researchpilot.literature.rag.embedding;

import java.util.List;

/** Provider-neutral boundary for embedding one or more controlled texts. */
@FunctionalInterface
public interface EmbeddingPort {

    /**
     * Embeds the texts exactly as supplied. Implementations must not normalize,
     * truncate, log, or otherwise rewrite them.
     */
    EmbeddingBatch embed(List<String> controlledTexts);
}
