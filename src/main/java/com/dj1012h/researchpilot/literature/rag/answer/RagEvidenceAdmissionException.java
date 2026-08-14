package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.Objects;

/** Safe orchestration failure carrying only stable failure semantics and a measured call count. */
public final class RagEvidenceAdmissionException extends RuntimeException {
    private final RagAnswerFailureType failureType;
    private final int relevanceJudgeCallCount;

    public RagEvidenceAdmissionException(
            RagAnswerFailureType failureType,
            int relevanceJudgeCallCount,
            Throwable cause
    ) {
        super(Objects.requireNonNull(failureType, "failureType must not be null").name(), cause);
        if (failureType != RagAnswerFailureType.RAG_RELEVANCE_JUDGE_UNAVAILABLE
                && failureType != RagAnswerFailureType.RAG_EVIDENCE_ADMISSION_INVALID) {
            throw new IllegalArgumentException("unsupported admission failure type");
        }
        if (relevanceJudgeCallCount < 0 || relevanceJudgeCallCount > 1) {
            throw new IllegalArgumentException("relevance judge call count must be zero or one");
        }
        this.failureType = failureType;
        this.relevanceJudgeCallCount = relevanceJudgeCallCount;
    }

    public RagAnswerFailureType failureType() {
        return failureType;
    }

    public int relevanceJudgeCallCount() {
        return relevanceJudgeCallCount;
    }
}
