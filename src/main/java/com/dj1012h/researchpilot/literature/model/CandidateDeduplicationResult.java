package com.dj1012h.researchpilot.literature.model;

import java.util.List;
import java.util.Objects;

public record CandidateDeduplicationResult(
        List<NormalizedCandidate> uniqueCandidates,
        List<DuplicateCandidateGroup> duplicateGroups,
        int inputCount,
        int uniqueCount,
        int removedCount
) {

    public CandidateDeduplicationResult {
        uniqueCandidates = List.copyOf(Objects.requireNonNull(uniqueCandidates,
                "uniqueCandidates must not be null"));
        duplicateGroups = List.copyOf(Objects.requireNonNull(duplicateGroups,
                "duplicateGroups must not be null"));
        if (inputCount < 0 || uniqueCount < 0 || removedCount < 0) {
            throw new IllegalArgumentException("deduplication counts must not be negative");
        }
        if (uniqueCount != uniqueCandidates.size()) {
            throw new IllegalArgumentException("uniqueCount must equal uniqueCandidates size");
        }
        if (removedCount != inputCount - uniqueCount) {
            throw new IllegalArgumentException("removedCount must equal inputCount - uniqueCount");
        }
    }

    public List<CandidatePaper> uniqueOriginalCandidates() {
        return uniqueCandidates.stream().map(NormalizedCandidate::originalCandidate).toList();
    }

    public static CandidateDeduplicationResult empty() {
        return new CandidateDeduplicationResult(List.of(), List.of(), 0, 0, 0);
    }
}
