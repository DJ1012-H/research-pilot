package com.dj1012h.researchpilot.literature.rag.index;

import java.util.Objects;

public class RagIndexRebuildException extends RuntimeException {

    private final String failureCode;

    public RagIndexRebuildException(String failureCode, String message) {
        super(message);
        this.failureCode = requireCode(failureCode);
    }

    public RagIndexRebuildException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = requireCode(failureCode);
    }

    public String failureCode() {
        return failureCode;
    }

    private static String requireCode(String value) {
        Objects.requireNonNull(value, "failureCode must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("failureCode must not be blank");
        return value;
    }
}
