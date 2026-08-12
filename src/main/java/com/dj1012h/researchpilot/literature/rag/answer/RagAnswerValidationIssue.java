package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.Objects;

public record RagAnswerValidationIssue(String code, String jsonPath, boolean retryable) {
    public RagAnswerValidationIssue {
        code = requireText(code, "code");
        jsonPath = requireText(jsonPath, "jsonPath");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
