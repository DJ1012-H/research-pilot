package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral evidence after the complete MySQL re-admission boundary.
 * The segment text is reconstructed by Java from the current MySQL paper and
 * is never copied from the Qdrant payload.
 */
public record TrustedRagEvidence(
        UUID pointId,
        long paperId,
        String normalizedDoi,
        String title,
        List<String> authors,
        Integer publicationYear,
        String venue,
        double score,
        RagSegmentType segmentType,
        int segmentIndex,
        String contentHash,
        Instant sourceUpdatedAt,
        String reconstructedSegmentText
) {
    public TrustedRagEvidence {
        pointId = Objects.requireNonNull(pointId, "pointId must not be null");
        if (paperId < 1) throw new IllegalArgumentException("paperId must be positive");
        normalizedDoi = requireText(normalizedDoi, "normalizedDoi");
        title = requireText(title, "title");
        authors = List.copyOf(Objects.requireNonNull(authors, "authors must not be null"));
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
        segmentType = Objects.requireNonNull(segmentType, "segmentType must not be null");
        if (segmentIndex < 0) throw new IllegalArgumentException("segmentIndex must not be negative");
        contentHash = requireText(contentHash, "contentHash");
        sourceUpdatedAt = Objects.requireNonNull(sourceUpdatedAt, "sourceUpdatedAt must not be null");
        reconstructedSegmentText = requireText(reconstructedSegmentText, "reconstructedSegmentText");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
