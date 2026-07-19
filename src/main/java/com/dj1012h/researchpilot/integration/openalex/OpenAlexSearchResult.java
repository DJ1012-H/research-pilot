package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.literature.model.CandidatePaper;

import java.util.List;

public record OpenAlexSearchResult(
        long totalMatches,
        List<CandidatePaper> candidates,
        String nextCursor
) {

    public OpenAlexSearchResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches 不能小于 0");
        }
    }
}
