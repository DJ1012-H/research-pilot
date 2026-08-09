package com.dj1012h.researchpilot.literature.rag.embedding;

import java.util.Objects;

/** Fail-closed embedding error that never carries request text or vector values. */
public class EmbeddingException extends RuntimeException {

    private final EmbeddingFailureType failureType;

    public EmbeddingException(EmbeddingFailureType failureType, String message) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
    }

    public EmbeddingException(EmbeddingFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
    }

    public EmbeddingFailureType failureType() {
        return failureType;
    }
}
