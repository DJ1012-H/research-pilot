package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagEvidence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Per-request evidence position derived from a trusted retrieval result. */
public record RagAnswerEvidence(
        String citationId,
        int evidencePosition,
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
        String segmentText
) {
    public RagAnswerEvidence {
        citationId = requireText(citationId, "citationId");
        if (evidencePosition < 1 || paperId < 1 || segmentIndex < 0) {
            throw new IllegalArgumentException("evidence position and identifiers are invalid");
        }
        normalizedDoi = requireText(normalizedDoi, "normalizedDoi");
        title = requireText(title, "title");
        authors = List.copyOf(Objects.requireNonNull(authors, "authors must not be null"));
        venue = Objects.requireNonNull(venue, "venue must not be null");
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score must be finite");
        segmentType = Objects.requireNonNull(segmentType, "segmentType must not be null");
        contentHash = requireText(contentHash, "contentHash");
        sourceUpdatedAt = Objects.requireNonNull(sourceUpdatedAt, "sourceUpdatedAt must not be null");
        segmentText = requireText(segmentText, "segmentText");
    }

    public static RagAnswerEvidence from(int position, TrustedRagEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");
        return new RagAnswerEvidence(
                "P" + position,
                position,
                evidence.paperId(),
                evidence.normalizedDoi(),
                evidence.title(),
                evidence.authors(),
                evidence.publicationYear(),
                evidence.venue(),
                evidence.score(),
                evidence.segmentType(),
                evidence.segmentIndex(),
                evidence.contentHash(),
                evidence.sourceUpdatedAt(),
                evidence.reconstructedSegmentText());
    }

    public RagAnswerCitation toPublicCitation() {
        return new RagAnswerCitation(
                citationId,
                evidencePosition,
                paperId,
                normalizedDoi,
                title,
                publicationYear,
                venue,
                segmentType,
                segmentIndex,
                contentHash,
                score);
    }

    public RagAnswerEvidence withPosition(int position) {
        return new RagAnswerEvidence(
                "P" + position,
                position,
                paperId,
                normalizedDoi,
                title,
                authors,
                publicationYear,
                venue,
                score,
                segmentType,
                segmentIndex,
                contentHash,
                sourceUpdatedAt,
                segmentText);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
