package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;

import java.util.Set;

/** Client-selectable filters after server-side business validation. */
public record RagSearchFilter(
        Integer fromYear,
        Integer toYear,
        Set<Long> paperIds,
        Set<RagSegmentType> segmentTypes
) {
    public RagSearchFilter {
        paperIds = Set.copyOf(paperIds == null ? Set.of() : paperIds);
        segmentTypes = Set.copyOf(segmentTypes == null ? Set.of() : segmentTypes);
    }
}
