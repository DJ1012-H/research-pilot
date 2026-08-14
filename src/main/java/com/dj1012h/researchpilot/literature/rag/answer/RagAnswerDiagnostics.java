package com.dj1012h.researchpilot.literature.rag.answer;

/** Safe counts only; no prompt, draft, provider message, DOI or paper list. */
public record RagAnswerDiagnostics(
        String failureCode,
        int modelCallCount,
        int relevanceJudgeCallCount,
        int answerModelCallCount,
        int admittedEvidenceCount,
        int generationEvidenceCount,
        int repairCount,
        int citationCount
) {
    public RagAnswerDiagnostics {
        if (modelCallCount < 0 || relevanceJudgeCallCount < 0 || answerModelCallCount < 0
                || admittedEvidenceCount < 0 || generationEvidenceCount < 0
                || repairCount < 0 || citationCount < 0) {
            throw new IllegalArgumentException("diagnostic counts must not be negative");
        }
        if (modelCallCount != relevanceJudgeCallCount + answerModelCallCount) {
            throw new IllegalArgumentException("total model call count must equal judge plus answer calls");
        }
        if (relevanceJudgeCallCount > 1) {
            throw new IllegalArgumentException("relevance judge call count exceeds one");
        }
        if (answerModelCallCount > 2 || repairCount > 1) {
            throw new IllegalArgumentException("answer call or repair count exceeds the hard maximum");
        }
        if (answerModelCallCount == 0 && repairCount != 0
                || answerModelCallCount > 0 && answerModelCallCount != repairCount + 1) {
            throw new IllegalArgumentException("answer model and repair counts are inconsistent");
        }
        if (admittedEvidenceCount > RagAnswerProperties.HARD_MAX_EVIDENCE) {
            throw new IllegalArgumentException("admitted evidence count exceeds the hard maximum");
        }
        if (generationEvidenceCount > RagAnswerProperties.HARD_MAX_EVIDENCE) {
            throw new IllegalArgumentException("generation evidence count exceeds the hard maximum");
        }
        if (generationEvidenceCount > admittedEvidenceCount) {
            throw new IllegalArgumentException("generation evidence count exceeds admitted evidence count");
        }
        if (answerModelCallCount == 0 && generationEvidenceCount != 0) {
            throw new IllegalArgumentException("generation evidence requires an answer model call");
        }
        if (citationCount > generationEvidenceCount) {
            throw new IllegalArgumentException("citation count exceeds generation evidence count");
        }
    }
}
