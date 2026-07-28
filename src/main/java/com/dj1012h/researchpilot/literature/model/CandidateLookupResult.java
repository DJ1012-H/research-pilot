package com.dj1012h.researchpilot.literature.model;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One deterministic Crossref lookup outcome for one deduplicated candidate.
 *
 * <p>This preserves the candidate-to-reference association needed by field
 * verification. It is a discovery result, not a verification decision.</p>
 */
public record CandidateLookupResult(
        NormalizedCandidate candidate,
        LookupRoute route,
        LookupStatus status,
        List<CrossrefWorkMetadata> references,
        String reason
) {

    public CandidateLookupResult {
        candidate = Objects.requireNonNull(candidate, "candidate must not be null");
        route = Objects.requireNonNull(route, "route must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        references = List.copyOf(Objects.requireNonNull(references, "references must not be null"));
        reason = requireText(reason, "reason");
        if (status == LookupStatus.FOUND && references.isEmpty()) {
            throw new IllegalArgumentException("FOUND must retain at least one reference");
        }
        if (status != LookupStatus.FOUND && !references.isEmpty()) {
            throw new IllegalArgumentException("only FOUND may retain references");
        }
    }

    public enum LookupRoute {
        DOI,
        BIBLIOGRAPHIC,
        NONE
    }

    public enum LookupStatus {
        FOUND,
        NOT_FOUND,
        SOURCE_DISABLED,
        SOURCE_UNAVAILABLE,
        FAILED,
        SKIPPED_BY_LIMIT,
        NOT_ELIGIBLE
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
