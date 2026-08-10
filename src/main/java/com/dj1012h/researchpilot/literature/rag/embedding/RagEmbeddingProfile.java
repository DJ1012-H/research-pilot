package com.dj1012h.researchpilot.literature.rag.embedding;

import java.util.Objects;

/** Versioned compatibility profile for the text template, model, and vector dimension. */
public record RagEmbeddingProfile(String model, String version, int expectedDimensions) {

    public static final String INITIAL_MODEL = "qwen3-embedding:0.6b";
    public static final String INITIAL_VERSION = "qe06b-d1024-t1-c350-o30-n1";
    public static final int INITIAL_DIMENSIONS = 1024;

    public RagEmbeddingProfile {
        model = canonicalText(model, "model");
        version = canonicalText(version, "version");
        if (expectedDimensions < 1) {
            throw new IllegalArgumentException("expectedDimensions must be positive");
        }
        if (INITIAL_VERSION.equals(version)
                && (!INITIAL_MODEL.equals(model) || expectedDimensions != INITIAL_DIMENSIONS)) {
            throw new IllegalArgumentException(
                    "the initial embedding version requires qwen3-embedding:0.6b at 1024 dimensions");
        }
    }

    public static RagEmbeddingProfile initial() {
        return new RagEmbeddingProfile(INITIAL_MODEL, INITIAL_VERSION, INITIAL_DIMENSIONS);
    }

    private static String canonicalText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be non-blank canonical text");
        }
        if (value.indexOf('|') >= 0) {
            throw new IllegalArgumentException(field + " must not contain the point-name separator");
        }
        return value;
    }
}
