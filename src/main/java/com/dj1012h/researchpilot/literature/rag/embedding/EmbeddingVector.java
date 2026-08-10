package com.dj1012h.researchpilot.literature.rag.embedding;

import java.util.List;
import java.util.Objects;

/** Immutable, finite, non-empty embedding vector. */
public record EmbeddingVector(List<Double> values) {

    public EmbeddingVector {
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("embedding vector must contain only finite values");
            }
        }
    }

    public int dimensions() {
        return values.size();
    }
}
