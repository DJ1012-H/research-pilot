package com.dj1012h.researchpilot.literature.rag;

import java.util.Objects;
import java.util.regex.Pattern;

/** Exact normalized text unit sent to the embedding port. */
public record RagDocumentSegment(
        RagSegmentType segmentType,
        int segmentIndex,
        String text,
        String contentHash
) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public RagDocumentSegment {
        segmentType = Objects.requireNonNull(segmentType, "segmentType must not be null");
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must not be negative");
        }
        text = requireText(text, "text");
        contentHash = requireText(contentHash, "contentHash");
        if (!SHA_256.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be lowercase SHA-256 hex");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
