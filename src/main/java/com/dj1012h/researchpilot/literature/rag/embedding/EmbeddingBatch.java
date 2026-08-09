package com.dj1012h.researchpilot.literature.rag.embedding;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable embedding response with the measured vector dimension and request latency. */
public record EmbeddingBatch(
        String model,
        List<EmbeddingVector> embeddings,
        int dimensions,
        Duration elapsed
) {

    public EmbeddingBatch {
        model = requireText(model, "model");
        embeddings = List.copyOf(Objects.requireNonNull(embeddings, "embeddings must not be null"));
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("embeddings must not be empty");
        }
        if (dimensions < 1) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (embeddings.stream().anyMatch(vector -> vector == null || vector.dimensions() != dimensions)) {
            throw new IllegalArgumentException("all embeddings must have the measured dimension");
        }
        elapsed = Objects.requireNonNull(elapsed, "elapsed must not be null");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
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
