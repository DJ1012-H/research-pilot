package com.dj1012h.researchpilot.literature.model;

import java.util.Objects;

/** Immutable candidate view containing normalized identity fields plus the untouched source object. */
public record NormalizedCandidate(
        String candidateId,
        CandidatePaper originalCandidate,
        String normalizedDoi,
        String normalizedOpenAlexId,
        String normalizedTitle,
        String normalizedFirstAuthor,
        Integer publicationYear,
        String normalizedVenue,
        int inputIndex
) {

    public NormalizedCandidate {
        candidateId = requireText(candidateId, "candidateId");
        originalCandidate = Objects.requireNonNull(originalCandidate, "originalCandidate must not be null");
        if (inputIndex < 0) {
            throw new IllegalArgumentException("inputIndex must not be negative");
        }
    }

    public CandidatePaper.CandidateSource candidateSource() {
        return originalCandidate.candidateSource();
    }

    public String originalDoi() {
        return originalCandidate.doi();
    }

    public String originalOpenAlexId() {
        return originalCandidate.openAlexId();
    }

    public String originalTitle() {
        return originalCandidate.title();
    }

    public String originalVenue() {
        return originalCandidate.sourceName();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
