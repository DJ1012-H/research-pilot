package com.dj1012h.researchpilot.literature.validation;

import java.util.Objects;

public record ValidationIssue(
        String code,
        String jsonPath,
        String message,
        boolean retryable
) {

    public ValidationIssue {
        code = requireText(code, "code");
        jsonPath = requireText(jsonPath, "jsonPath");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
