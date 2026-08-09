package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.model.VerificationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable, embedded paper segment ready for a later derived-index adapter. */
public record VerifiedPaperProjection(
        UUID pointId,
        long paperId,
        String doi,
        String title,
        Integer publicationYear,
        String venue,
        String language,
        VerificationResult.VerificationStatus verificationStatus,
        String verificationVersion,
        RagSegmentType segmentType,
        int segmentIndex,
        String embeddingModel,
        String embeddingVersion,
        String contentHash,
        Instant sourceUpdatedAt,
        String text,
        List<Double> vector,
        int vectorDimensions,
        Duration embeddingElapsed
) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public VerifiedPaperProjection {
        pointId = Objects.requireNonNull(pointId, "pointId must not be null");
        if (paperId < 1) throw new IllegalArgumentException("paperId must be positive");
        doi = requireText(doi, "doi");
        title = requireText(title, "title");
        venue = Objects.requireNonNull(venue, "venue must not be null");
        language = Objects.requireNonNull(language, "language must not be null");
        verificationStatus = Objects.requireNonNull(verificationStatus, "verificationStatus must not be null");
        if (verificationStatus != VerificationResult.VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("projection verificationStatus must be VERIFIED");
        }
        verificationVersion = requireText(verificationVersion, "verificationVersion");
        segmentType = Objects.requireNonNull(segmentType, "segmentType must not be null");
        if (segmentIndex < 0) throw new IllegalArgumentException("segmentIndex must not be negative");
        embeddingModel = requireText(embeddingModel, "embeddingModel");
        embeddingVersion = requireText(embeddingVersion, "embeddingVersion");
        contentHash = requireText(contentHash, "contentHash");
        if (!SHA_256.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be lowercase SHA-256 hex");
        }
        sourceUpdatedAt = Objects.requireNonNull(sourceUpdatedAt, "sourceUpdatedAt must not be null");
        text = requireText(text, "text");
        vector = List.copyOf(Objects.requireNonNull(vector, "vector must not be null"));
        if (vectorDimensions < 1 || vector.size() != vectorDimensions) {
            throw new IllegalArgumentException("vector must have the measured dimensions");
        }
        if (vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("vector must contain only finite values");
        }
        embeddingElapsed = Objects.requireNonNull(embeddingElapsed, "embeddingElapsed must not be null");
        if (embeddingElapsed.isNegative()) {
            throw new IllegalArgumentException("embeddingElapsed must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
