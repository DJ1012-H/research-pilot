package com.dj1012h.researchpilot.integration.crossref;

import java.util.List;
import java.util.Objects;

/** Bounded Crossref bibliographic-search outcome; it deliberately does not select a best match. */
public record CrossrefBibliographicLookupResult(Status status, List<CrossrefWorkMetadata> candidates) {
    public CrossrefBibliographicLookupResult {
        status = Objects.requireNonNull(status, "status must not be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if ((status == Status.NOT_FOUND) != candidates.isEmpty()) {
            throw new IllegalArgumentException("NOT_FOUND must have no candidates");
        }
    }

    public static CrossrefBibliographicLookupResult notFound() {
        return new CrossrefBibliographicLookupResult(Status.NOT_FOUND, List.of());
    }

    public static CrossrefBibliographicLookupResult found(List<CrossrefWorkMetadata> candidates) {
        return new CrossrefBibliographicLookupResult(
                candidates.size() == 1 ? Status.FOUND_SINGLE : Status.FOUND_MULTIPLE,
                candidates
        );
    }

    public enum Status { NOT_FOUND, FOUND_SINGLE, FOUND_MULTIPLE }
}
