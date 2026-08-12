package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;

/** Bounded public request; answer-specific index and model controls are absent by design. */
public record ResearchQuestionRequest(
        String question,
        Integer topK,
        Integer fromYear,
        Integer toYear,
        List<Long> paperIds
) {
    public ResearchQuestionRequest {
        paperIds = paperIds == null ? List.of() : List.copyOf(paperIds);
    }
}
