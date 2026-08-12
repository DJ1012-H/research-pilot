package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;

import java.util.List;

/** JSON request for the opt-in Day 4 diagnostics endpoint. */
public record RagRetrievalRequest(
        String query,
        Integer topK,
        Integer fromYear,
        Integer toYear,
        List<Long> paperIds,
        List<RagSegmentType> segmentTypes
) {
    public RagRetrievalRequest {
        paperIds = paperIds == null ? List.of() : List.copyOf(paperIds);
        segmentTypes = segmentTypes == null ? List.of() : List.copyOf(segmentTypes);
    }
}
