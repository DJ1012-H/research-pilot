package com.dj1012h.researchpilot.literature.api.dto;

import java.util.List;
import java.util.Objects;

/** Always-present, fail-closed public review result. */
public record ReviewResponse(
        ReviewStatus status,
        String summary,
        List<ReviewCitation> citations,
        String message
) {
    public ReviewResponse {
        status = Objects.requireNonNull(status, "status must not be null");
        summary = Objects.requireNonNull(summary, "summary must not be null");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations must not be null"));
        message = requireText(message, "message");

        if (status == ReviewStatus.GENERATED) {
            if (summary.isBlank() || citations.isEmpty()) {
                throw new IllegalArgumentException("generated review must contain summary and citations");
            }
        } else if (!summary.isEmpty() || !citations.isEmpty()) {
            throw new IllegalArgumentException("degraded review must not expose summary or citations");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public enum ReviewStatus {
        GENERATED,
        INSUFFICIENT_EVIDENCE,
        INPUT_BUDGET_EXCEEDED,
        VALIDATION_FAILED,
        GENERATION_UNAVAILABLE,
        DEADLINE_EXCEEDED
    }
}
