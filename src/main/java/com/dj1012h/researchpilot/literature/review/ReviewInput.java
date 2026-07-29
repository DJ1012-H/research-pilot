package com.dj1012h.researchpilot.literature.review;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable evidence package that may be supplied to an internal review model. */
public record ReviewInput(
        int requestedCount,
        int verifiedPaperCount,
        int abstractEvidenceCount,
        List<EvidencePaper> evidencePapers
) {
    public ReviewInput {
        if (requestedCount < 1 || verifiedPaperCount < 0 || abstractEvidenceCount < 0) {
            throw new IllegalArgumentException("review counts must be non-negative and requestedCount positive");
        }
        evidencePapers = List.copyOf(Objects.requireNonNull(evidencePapers, "evidencePapers must not be null"));
        if (abstractEvidenceCount != evidencePapers.size()) {
            throw new IllegalArgumentException("abstractEvidenceCount must equal evidencePapers size");
        }
        if (verifiedPaperCount < abstractEvidenceCount) {
            throw new IllegalArgumentException("verifiedPaperCount must cover abstract evidence");
        }
        Set<CitationId> citationIds = new HashSet<>();
        Set<String> dois = new HashSet<>();
        for (EvidencePaper paper : evidencePapers) {
            Objects.requireNonNull(paper, "evidencePapers must not contain null");
            if (!citationIds.add(paper.citationId())) {
                throw new IllegalArgumentException("duplicate citationId");
            }
            if (!dois.add(paper.normalizedDoi())) {
                throw new IllegalArgumentException("duplicate normalizedDoi");
            }
        }
    }
}
