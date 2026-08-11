package com.dj1012h.researchpilot.literature.rag.index;

import java.util.Objects;

public class RagIndexException extends RuntimeException {

    private final RagIndexFailureType failureType;

    public RagIndexException(RagIndexFailureType failureType, String message) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
    }

    public RagIndexException(RagIndexFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
    }

    public RagIndexFailureType failureType() {
        return failureType;
    }
}
