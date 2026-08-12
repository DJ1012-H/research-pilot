package com.dj1012h.researchpilot.literature.rag.answer;

/** Safe counts only; no prompt, draft, provider message, DOI or paper list. */
public record RagAnswerDiagnostics(
        String failureCode,
        int modelCallCount,
        int repairCount,
        int citationCount
) {
    public RagAnswerDiagnostics {
        if (modelCallCount < 0 || repairCount < 0 || citationCount < 0) {
            throw new IllegalArgumentException("diagnostic counts must not be negative");
        }
    }
}
