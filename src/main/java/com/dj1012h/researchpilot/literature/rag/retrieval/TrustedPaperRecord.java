package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;

import java.time.Instant;
import java.util.Objects;

/** Current MySQL paper state used to re-admit an index candidate. */
public record TrustedPaperRecord(
        long paperId,
        PaperDTO paper,
        VerificationResult.VerificationStatus currentVerificationStatus,
        String normalizedDoi,
        String verificationVersion,
        Instant sourceUpdatedAt
) {
    public TrustedPaperRecord {
        if (paperId < 1) throw new IllegalArgumentException("paperId must be positive");
        paper = Objects.requireNonNull(paper, "paper must not be null");
        currentVerificationStatus = Objects.requireNonNull(
                currentVerificationStatus, "currentVerificationStatus must not be null");
    }
}
