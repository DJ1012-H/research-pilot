package com.dj1012h.researchpilot.literature.model;

import java.util.List;
import java.util.Objects;

public record DuplicateCandidateGroup(
        CandidateDeduplicationKey key,
        String retainedCandidateId,
        List<String> removedCandidateIds,
        DeduplicationReason reason
) {

    public DuplicateCandidateGroup {
        key = Objects.requireNonNull(key, "key must not be null");
        if (retainedCandidateId == null || retainedCandidateId.isBlank()) {
            throw new IllegalArgumentException("retainedCandidateId must not be blank");
        }
        removedCandidateIds = List.copyOf(Objects.requireNonNull(
                removedCandidateIds, "removedCandidateIds must not be null"));
        if (removedCandidateIds.isEmpty()) {
            throw new IllegalArgumentException("removedCandidateIds must not be empty");
        }
        reason = Objects.requireNonNull(reason, "reason must not be null");
    }
}
