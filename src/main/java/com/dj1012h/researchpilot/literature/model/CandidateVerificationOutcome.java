package com.dj1012h.researchpilot.literature.model;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;

import java.util.Objects;

/** Final verification outcome for one deduplicated OpenAlex candidate. */
public record CandidateVerificationOutcome(
        CandidatePaper candidate,
        CrossrefWorkMetadata selectedReference,
        VerificationResult verification
) {
    public CandidateVerificationOutcome {
        candidate = Objects.requireNonNull(candidate, "candidate must not be null");
        verification = Objects.requireNonNull(verification, "verification must not be null");
        if (verification.status() == VerificationResult.VerificationStatus.VERIFIED
                && selectedReference == null) {
            throw new IllegalArgumentException("VERIFIED must retain its selected Crossref reference");
        }
    }
}
