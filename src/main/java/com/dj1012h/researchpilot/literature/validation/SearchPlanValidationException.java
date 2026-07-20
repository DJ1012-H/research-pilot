package com.dj1012h.researchpilot.literature.validation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SearchPlanValidationException extends RuntimeException {

    private final ValidationStage stage;
    private final List<ValidationIssue> issues;

    public SearchPlanValidationException(ValidationStage stage, List<ValidationIssue> issues) {
        super(message(stage, issues));
        this.stage = Objects.requireNonNull(stage, "stage 不能为空");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues 不能为空"));
        if (this.issues.isEmpty()) {
            throw new IllegalArgumentException("issues 不能为空");
        }
    }

    public ValidationStage getStage() {
        return stage;
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    public boolean isRetryable() {
        return issues.stream().allMatch(ValidationIssue::retryable);
    }

    private static String message(ValidationStage stage, List<ValidationIssue> issues) {
        String codes = issues == null ? "unknown" : issues.stream()
                .map(ValidationIssue::code)
                .collect(Collectors.joining(","));
        return "Search plan validation failed at " + stage + ": " + codes;
    }
}
