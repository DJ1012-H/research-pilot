package com.dj1012h.researchpilot.literature.review;

import java.util.Objects;
import java.util.Optional;

/** Safe internal outcome; raw drafts and prompts are deliberately absent. */
public record ReviewOutcome(
        ReviewOutcomeStatus status,
        Optional<ValidatedReview> validatedReview,
        Optional<ReviewInput> reviewInput,
        int modelCallCount,
        int repairCount,
        int evidenceCount,
        Optional<String> failureCode
) {
    public ReviewOutcome {
        status = Objects.requireNonNull(status, "status must not be null");
        validatedReview = Objects.requireNonNull(validatedReview, "validatedReview must not be null");
        reviewInput = Objects.requireNonNull(reviewInput, "reviewInput must not be null");
        failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
        if (modelCallCount < 0 || modelCallCount > 2) {
            throw new IllegalArgumentException("modelCallCount must be between 0 and 2");
        }
        if (repairCount < 0 || repairCount > 1 || repairCount > modelCallCount) {
            throw new IllegalArgumentException("repairCount must be zero or one and covered by calls");
        }
        if (evidenceCount < 0) {
            throw new IllegalArgumentException("evidenceCount must not be negative");
        }
        boolean generated = status == ReviewOutcomeStatus.GENERATED;
        if (generated != validatedReview.isPresent() || generated != reviewInput.isPresent()) {
            throw new IllegalArgumentException("only generated outcomes may expose validated review data");
        }
        if (generated && failureCode.isPresent()) {
            throw new IllegalArgumentException("generated outcome must not contain failureCode");
        }
    }

    public static ReviewOutcome generated(
            ValidatedReview review,
            ReviewInput input,
            int modelCallCount,
            int repairCount
    ) {
        return new ReviewOutcome(
                ReviewOutcomeStatus.GENERATED,
                Optional.of(review),
                Optional.of(input),
                modelCallCount,
                repairCount,
                input.evidencePapers().size(),
                Optional.empty()
        );
    }

    public static ReviewOutcome failed(
            ReviewOutcomeStatus status,
            int modelCallCount,
            int repairCount,
            int evidenceCount,
            String failureCode
    ) {
        if (status == ReviewOutcomeStatus.GENERATED) {
            throw new IllegalArgumentException("failed outcome cannot be GENERATED");
        }
        return new ReviewOutcome(
                status,
                Optional.empty(),
                Optional.empty(),
                modelCallCount,
                repairCount,
                evidenceCount,
                Optional.of(requireCode(failureCode))
        );
    }

    private static String requireCode(String value) {
        Objects.requireNonNull(value, "failureCode must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("failureCode must not be blank");
        }
        return value;
    }
}
