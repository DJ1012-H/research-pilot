package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Safe validation metadata; it never stores the raw draft or evidence text. */
public class RagAnswerValidationException extends RuntimeException {
    private final RagAnswerValidationStage stage;
    private final List<RagAnswerValidationIssue> issues;

    public RagAnswerValidationException(
            RagAnswerValidationStage stage,
            List<RagAnswerValidationIssue> issues
    ) {
        super("RAG answer draft validation failed at " + stage);
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        if (this.issues.isEmpty()) throw new IllegalArgumentException("issues must not be empty");
    }

    public RagAnswerValidationStage stage() { return stage; }
    public List<RagAnswerValidationIssue> issues() { return issues; }
    public boolean isRetryable() { return issues.stream().allMatch(RagAnswerValidationIssue::retryable); }
    public List<String> safeCodes() { return issues.stream().map(RagAnswerValidationIssue::code).distinct().sorted().toList(); }
}
