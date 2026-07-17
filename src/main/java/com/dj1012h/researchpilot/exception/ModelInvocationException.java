package com.dj1012h.researchpilot.exception;

public class ModelInvocationException extends RuntimeException {

    private final ModelFailureType failureType;

    public ModelInvocationException(ModelFailureType failureType, Throwable cause) {
        super("Model invocation failed: " + failureType, cause);
        this.failureType = failureType;
    }

    public ModelFailureType getFailureType() {
        return failureType;
    }
}
