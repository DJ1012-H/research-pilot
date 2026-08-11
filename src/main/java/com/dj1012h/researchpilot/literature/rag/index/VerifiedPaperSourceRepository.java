package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.rag.VerifiedPaperSource;

import java.util.List;

/** Authoritative MySQL read boundary for papers currently eligible for indexing. */
public interface VerifiedPaperSourceRepository {

    List<VerifiedPaperSource> findCurrentlyVerified();
}
