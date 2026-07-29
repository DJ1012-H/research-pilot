package com.dj1012h.researchpilot.literature.review;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ReviewDraftValidationException extends RuntimeException {

    private final ReviewValidationStage stage;
    private final List<ReviewValidationIssue> issues;

    public ReviewDraftValidationException(
            ReviewValidationStage stage,
            List<ReviewValidationIssue> issues
    ) {
        super(message(stage, issues));
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        if (this.issues.isEmpty()) {
            throw new IllegalArgumentException("issues must not be empty");
        }
    }

    public ReviewValidationStage getStage() {
        return stage;
    }

    public List<ReviewValidationIssue> getIssues() {
        return issues;
    }

    public boolean isRetryable() {
        return issues.stream().allMatch(ReviewValidationIssue::retryable);
    }

    public List<String> safeCodes() {
        return issues.stream().map(ReviewValidationIssue::code).distinct().sorted().toList();
    }

    private static String message(
            ReviewValidationStage stage,
            List<ReviewValidationIssue> issues
    ) {
        String codes = issues == null ? "unknown" : issues.stream()
                .map(ReviewValidationIssue::code)
                .collect(Collectors.joining(","));
        return "Review draft validation failed at " + stage + ": " + codes;
    }
}
