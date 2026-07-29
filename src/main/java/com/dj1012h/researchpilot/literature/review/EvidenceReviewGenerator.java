package com.dj1012h.researchpilot.literature.review;

/** Produces raw, untrusted review text for an eligible internal preparation only. */
public interface EvidenceReviewGenerator {
    UntrustedReviewDraft generate(String prompt);
}
