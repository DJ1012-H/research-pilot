package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.Objects;

/** Safe orchestration failure carrying only stable failure semantics and a measured call count. */
public final class RagEvidenceAdmissionException extends RuntimeException {
    private final RagAnswerFailureType failureType;
    private final int relevanceJudgeCallCount;
    private final String failureDetailCode;

    public RagEvidenceAdmissionException(
            RagAnswerFailureType failureType,
            int relevanceJudgeCallCount,
            Throwable cause
    ) {
        this(failureType, relevanceJudgeCallCount, null, cause);
    }

    public RagEvidenceAdmissionException(
            RagAnswerFailureType failureType,
            int relevanceJudgeCallCount,
            String failureDetailCode,
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
        if (failureDetailCode != null && !failureDetailCode.matches("RAG_ADMISSION_[A-Z0-9_]+")) {
            throw new IllegalArgumentException("invalid admission failure detail code");
        }
        this.failureType = failureType;
        this.relevanceJudgeCallCount = relevanceJudgeCallCount;
        this.failureDetailCode = failureDetailCode;
    }

    public RagAnswerFailureType failureType() {
        return failureType;
    }

    public int relevanceJudgeCallCount() {
        return relevanceJudgeCallCount;
    }

    public String failureDetailCode() {
        return failureDetailCode;
    }
}
