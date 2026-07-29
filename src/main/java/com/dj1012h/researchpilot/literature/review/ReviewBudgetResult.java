package com.dj1012h.researchpilot.literature.review;

import java.util.Objects;
import java.util.Optional;

public record ReviewBudgetResult(
        ReviewBudgetStatus status,
        Optional<ReviewInput> reviewInput
) {
    public ReviewBudgetResult {
        status = Objects.requireNonNull(status, "status must not be null");
        reviewInput = Objects.requireNonNull(reviewInput, "reviewInput must not be null");
        if (status == ReviewBudgetStatus.READY && reviewInput.isEmpty()) {
            throw new IllegalArgumentException("ready budget result requires input");
        }
        if (status != ReviewBudgetStatus.READY && reviewInput.isPresent()) {
            throw new IllegalArgumentException("exceeded budget result must not expose input");
        }
    }

    public static ReviewBudgetResult ready(ReviewInput input) {
        return new ReviewBudgetResult(ReviewBudgetStatus.READY, Optional.of(input));
    }

    public static ReviewBudgetResult exceeded() {
        return new ReviewBudgetResult(ReviewBudgetStatus.INPUT_BUDGET_EXCEEDED, Optional.empty());
    }

    public enum ReviewBudgetStatus {
        READY,
        INPUT_BUDGET_EXCEEDED
    }
}
