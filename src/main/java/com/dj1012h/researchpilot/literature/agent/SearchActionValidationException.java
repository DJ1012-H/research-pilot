package com.dj1012h.researchpilot.literature.agent;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SearchActionValidationException extends RuntimeException {

    private final SearchActionValidationStage stage;
    private final List<SearchActionValidationIssue> issues;

    public SearchActionValidationException(SearchActionValidationStage stage, List<SearchActionValidationIssue> issues) {
        super(message(stage, issues));
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        if (this.issues.isEmpty()) throw new IllegalArgumentException("issues must not be empty");
    }

    public SearchActionValidationStage getStage() { return stage; }
    public List<SearchActionValidationIssue> getIssues() { return issues; }

    private static String message(SearchActionValidationStage stage, List<SearchActionValidationIssue> issues) {
        String codes = issues == null ? "unknown" : issues.stream()
                .map(SearchActionValidationIssue::code).collect(Collectors.joining(","));
        return "Search action validation failed at " + stage + ": " + codes;
    }
}
