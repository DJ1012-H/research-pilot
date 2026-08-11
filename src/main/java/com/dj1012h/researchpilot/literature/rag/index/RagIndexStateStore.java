package com.dj1012h.researchpilot.literature.rag.index;

import java.time.Instant;
import java.util.Optional;

/** MySQL boundary for build evidence and active embedding-version selection. */
public interface RagIndexStateStore {

    void begin(RagIndexDefinition definition, Instant startedAt);

    void activate(
            RagIndexDefinition definition,
            int sourcePaperCount,
            long pointCount,
            Instant completedAt);

    void fail(RagIndexDefinition definition, String failureCode, Instant completedAt);

    Optional<RagIndexVersionState> active();
}
