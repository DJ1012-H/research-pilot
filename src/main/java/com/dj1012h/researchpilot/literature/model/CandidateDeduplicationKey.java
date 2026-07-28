package com.dj1012h.researchpilot.literature.model;

import java.util.Objects;
import java.util.Optional;

public record CandidateDeduplicationKey(
        DeduplicationKeyType type,
        String value
) {

    /**
     * Uses the same conservative identity precedence as candidate deduplication.
     * An empty result intentionally means that no stable cross-round identity is
     * available for the candidate.
     */
    public static Optional<CandidateDeduplicationKey> from(NormalizedCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (hasText(candidate.normalizedDoi())) {
            return Optional.of(new CandidateDeduplicationKey(DeduplicationKeyType.DOI, candidate.normalizedDoi()));
        }
        if (hasText(candidate.normalizedOpenAlexId())) {
            return Optional.of(new CandidateDeduplicationKey(
                    DeduplicationKeyType.OPENALEX_ID, candidate.normalizedOpenAlexId()));
        }
        if (hasText(candidate.normalizedTitle())
                && hasText(candidate.normalizedFirstAuthor())
                && candidate.publicationYear() != null) {
            return Optional.of(new CandidateDeduplicationKey(
                    DeduplicationKeyType.BIBLIOGRAPHIC,
                    candidate.normalizedTitle() + "|"
                            + candidate.normalizedFirstAuthor() + "|"
                            + candidate.publicationYear()));
        }
        return Optional.empty();
    }

    public CandidateDeduplicationKey {
        type = Objects.requireNonNull(type, "type must not be null");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return type.name() + ":" + value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
