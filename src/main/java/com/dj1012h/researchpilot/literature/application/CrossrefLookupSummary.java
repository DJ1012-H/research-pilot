package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;

import java.util.List;
import java.util.Objects;

/** Internal-only discovery outcome. It is not a paper-verification result. */
public record CrossrefLookupSummary(
        int doiEligibleCount,
        int titleEligibleCount,
        int attemptedCount,
        int foundCount,
        int notFoundCount,
        int failedCount,
        int skippedByLimitCount,
        boolean crossrefEnabled,
        boolean sourceAvailable,
        List<CrossrefWorkMetadata> foundMetadata,
        List<CrossrefBibliographicLookupResult> bibliographicResults,
        CandidateDeduplicationResult candidateDeduplication
) {
    /** Compatibility constructor for callers that only observe DOI lookup accounting. */
    public CrossrefLookupSummary(
            int doiEligibleCount, int attemptedCount, int foundCount, int notFoundCount, int failedCount,
            int skippedByLimitCount, boolean crossrefEnabled, boolean sourceAvailable,
            List<CrossrefWorkMetadata> foundMetadata
    ) {
        this(doiEligibleCount, 0, attemptedCount, foundCount, notFoundCount, failedCount,
                skippedByLimitCount, crossrefEnabled, sourceAvailable, foundMetadata, List.of(),
                CandidateDeduplicationResult.empty());
    }

    /** Compatibility constructor for the pre-deduplication summary shape. */
    public CrossrefLookupSummary(
            int doiEligibleCount, int titleEligibleCount, int attemptedCount, int foundCount,
            int notFoundCount, int failedCount, int skippedByLimitCount, boolean crossrefEnabled,
            boolean sourceAvailable, List<CrossrefWorkMetadata> foundMetadata,
            List<CrossrefBibliographicLookupResult> bibliographicResults
    ) {
        this(doiEligibleCount, titleEligibleCount, attemptedCount, foundCount, notFoundCount, failedCount,
                skippedByLimitCount, crossrefEnabled, sourceAvailable, foundMetadata, bibliographicResults,
                CandidateDeduplicationResult.empty());
    }

    public CrossrefLookupSummary {
        if (doiEligibleCount < 0 || titleEligibleCount < 0 || attemptedCount < 0 || foundCount < 0
                || notFoundCount < 0 || failedCount < 0 || skippedByLimitCount < 0) {
            throw new IllegalArgumentException("Crossref lookup counts must not be negative");
        }
        if (attemptedCount != foundCount + notFoundCount + failedCount) {
            throw new IllegalArgumentException("attemptedCount must equal result counts");
        }
        foundMetadata = List.copyOf(Objects.requireNonNull(foundMetadata, "foundMetadata must not be null"));
        bibliographicResults = List.copyOf(Objects.requireNonNull(
                bibliographicResults, "bibliographicResults must not be null"));
        candidateDeduplication = Objects.requireNonNull(
                candidateDeduplication, "candidateDeduplication must not be null");
    }
}
