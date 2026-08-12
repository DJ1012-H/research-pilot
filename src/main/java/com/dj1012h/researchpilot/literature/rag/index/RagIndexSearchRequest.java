package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral, server-bounded vector search request. */
public record RagIndexSearchRequest(
        List<Double> queryVector,
        int limit,
        Integer fromYear,
        Integer toYear,
        Set<Long> paperIds,
        Set<RagSegmentType> segmentTypes
) {

    public RagIndexSearchRequest {
        queryVector = List.copyOf(Objects.requireNonNull(queryVector, "queryVector must not be null"));
        if (queryVector.isEmpty() || queryVector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("queryVector must contain finite values");
        }
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        if (fromYear != null && toYear != null && fromYear > toYear) {
            throw new IllegalArgumentException("fromYear must not exceed toYear");
        }
        paperIds = Set.copyOf(Objects.requireNonNull(paperIds, "paperIds must not be null"));
        if (paperIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("paperIds must be positive");
        }
        segmentTypes = Set.copyOf(Objects.requireNonNull(segmentTypes, "segmentTypes must not be null"));
    }
}
