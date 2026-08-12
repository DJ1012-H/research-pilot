package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;

import java.util.Objects;

/** Public citation assembled from trusted Java evidence, never from model metadata. */
public record RagAnswerCitation(
        String citationId,
        int evidencePosition,
        long paperId,
        String normalizedDoi,
        String title,
        Integer publicationYear,
        String venue,
        RagSegmentType segmentType,
        int segmentIndex,
        String contentHash,
        Double score
) {
    public RagAnswerCitation {
        citationId = requireText(citationId, "citationId");
        if (evidencePosition < 1 || paperId < 1 || segmentIndex < 0) {
            throw new IllegalArgumentException("citation positions and identifiers are invalid");
        }
        normalizedDoi = requireText(normalizedDoi, "normalizedDoi");
        title = requireText(title, "title");
        venue = Objects.requireNonNull(venue, "venue must not be null");
        segmentType = Objects.requireNonNull(segmentType, "segmentType must not be null");
        contentHash = requireText(contentHash, "contentHash");
        if (score != null && !Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
