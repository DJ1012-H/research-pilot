package com.dj1012h.researchpilot.literature.rag.index;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable collection identity and vector contract for one embedding version. */
public record RagIndexDefinition(
        String collectionName,
        String embeddingVersion,
        int vectorDimensions
) {

    private static final Pattern COLLECTION_NAME = Pattern.compile("[A-Za-z0-9_-]{1,255}");

    public RagIndexDefinition {
        collectionName = requireText(collectionName, "collectionName");
        if (!COLLECTION_NAME.matcher(collectionName).matches()) {
            throw new IllegalArgumentException("collectionName contains unsupported characters");
        }
        embeddingVersion = requireText(embeddingVersion, "embeddingVersion");
        if (vectorDimensions < 1) throw new IllegalArgumentException("vectorDimensions must be positive");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
