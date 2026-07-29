package com.dj1012h.researchpilot.literature.review;

import java.util.Objects;
import java.util.Optional;

/** Auditable gate result; an input exists only when review generation is allowed. */
public record ReviewPreparationResult(
        ReviewEligibility eligibility,
        int requestedCount,
        int requiredVerifiedCount,
        int formalVerifiedPaperCount,
        int abstractEvidenceCount,
        Optional<ReviewInput> reviewInput
) {
    public ReviewPreparationResult {
        eligibility = Objects.requireNonNull(eligibility, "eligibility must not be null");
        reviewInput = Objects.requireNonNull(reviewInput, "reviewInput must not be null");
        if (requestedCount < 1 || requiredVerifiedCount < 1
                || formalVerifiedPaperCount < 0 || abstractEvidenceCount < 0) {
            throw new IllegalArgumentException("review preparation counts are invalid");
        }
        if (formalVerifiedPaperCount < abstractEvidenceCount) {
            throw new IllegalArgumentException("abstract evidence cannot exceed formal verified papers");
        }
        if (eligibility == ReviewEligibility.ELIGIBLE && reviewInput.isEmpty()) {
            throw new IllegalArgumentException("eligible preparation requires review input");
        }
        if (eligibility != ReviewEligibility.ELIGIBLE && reviewInput.isPresent()) {
            throw new IllegalArgumentException("ineligible preparation must not expose review input");
        }
    }

    public static ReviewPreparationResult ineligible(
            ReviewEligibility eligibility,
            int requestedCount,
            int requiredVerifiedCount,
            int formalVerifiedPaperCount,
            int abstractEvidenceCount
    ) {
        if (eligibility == ReviewEligibility.ELIGIBLE) {
            throw new IllegalArgumentException("ineligible result requires an insufficient-evidence status");
        }
        return new ReviewPreparationResult(
                eligibility, requestedCount, requiredVerifiedCount, formalVerifiedPaperCount,
                abstractEvidenceCount, Optional.empty()
        );
    }

    public static ReviewPreparationResult eligible(
            int requiredVerifiedCount,
            ReviewInput reviewInput
    ) {
        Objects.requireNonNull(reviewInput, "reviewInput must not be null");
        return new ReviewPreparationResult(
                ReviewEligibility.ELIGIBLE,
                reviewInput.requestedCount(),
                requiredVerifiedCount,
                reviewInput.verifiedPaperCount(),
                reviewInput.abstractEvidenceCount(),
                Optional.of(reviewInput)
        );
    }
}
