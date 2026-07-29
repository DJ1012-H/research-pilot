package com.dj1012h.researchpilot.literature.review;

import java.util.Objects;

/** Safe validation metadata that contains no model or evidence text. */
public record ReviewValidationIssue(
        String code,
        String jsonPath,
        boolean retryable
) {
    public ReviewValidationIssue {
        code = requireText(code, "code");
        jsonPath = requireText(jsonPath, "jsonPath");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
