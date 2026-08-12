package com.dj1012h.researchpilot.literature.rag.retrieval;

import java.util.Collection;
import java.util.List;

/** Batch MySQL read boundary for current paper state during retrieval re-admission. */
public interface TrustedPaperReadRepository {
    List<TrustedPaperRecord> findByPaperIds(Collection<Long> paperIds);
}
