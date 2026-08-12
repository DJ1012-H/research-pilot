package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;

import java.time.Instant;

/** A result after MySQL re-admission; business fields are authoritative-source fields. */
public record RagSearchHit(
        long paperId,
        String normalizedDoi,
        String title,
        Integer publicationYear,
        String venue,
        double score,
        RagSegmentType matchedSegmentType,
        int matchedSegmentIndex,
        String boundedExcerpt,
        String contentHash,
        Instant sourceUpdatedAt
) { }
