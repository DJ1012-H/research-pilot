package com.dj1012h.researchpilot.literature.review;

import java.util.Objects;
import java.util.Optional;

/** Internal outcome showing whether a model call was allowed by the evidence gate. */
public record ReviewGenerationAttempt(
        ReviewPreparationResult preparation,
        Optional<UntrustedReviewDraft> untrustedDraft
) {
    public ReviewGenerationAttempt {
        preparation = Objects.requireNonNull(preparation, "preparation must not be null");
        untrustedDraft = Objects.requireNonNull(untrustedDraft, "untrustedDraft must not be null");
        if (preparation.eligibility() == ReviewEligibility.ELIGIBLE && untrustedDraft.isEmpty()) {
            throw new IllegalArgumentException("eligible generation requires an untrusted draft");
        }
        if (preparation.eligibility() != ReviewEligibility.ELIGIBLE && untrustedDraft.isPresent()) {
            throw new IllegalArgumentException("ineligible generation must not contain a draft");
        }
    }
}
