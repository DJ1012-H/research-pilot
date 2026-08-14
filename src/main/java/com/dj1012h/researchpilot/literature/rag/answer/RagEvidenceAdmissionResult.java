package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Bounded admission result with explicit model-call accounting. */
public record RagEvidenceAdmissionResult(
        List<RagAnswerEvidence> admittedEvidence,
        int relevanceJudgeCallCount
) {
    public RagEvidenceAdmissionResult {
        admittedEvidence = List.copyOf(Objects.requireNonNull(
                admittedEvidence, "admittedEvidence must not be null"));
        if (admittedEvidence.size() > RagAnswerProperties.HARD_MAX_EVIDENCE) {
            throw new IllegalArgumentException("admitted evidence exceeds the hard maximum");
        }
        if (relevanceJudgeCallCount != 1) {
            throw new IllegalArgumentException("relevance judge must be called exactly once");
        }
    }
}
