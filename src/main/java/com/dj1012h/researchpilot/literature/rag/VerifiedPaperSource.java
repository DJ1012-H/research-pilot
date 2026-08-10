package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;

import java.time.Instant;

/** Authoritative MySQL/domain inputs required for one rebuildable projection. */
public record VerifiedPaperSource(
        long paperId,
        PaperDTO paper,
        VerificationResult verification,
        String normalizedDoi,
        String verificationVersion,
        Instant sourceUpdatedAt
) { }
