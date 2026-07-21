package com.dj1012h.researchpilot.integration.crossref;

import java.time.Duration;
import java.util.Objects;

public class CrossrefApiException extends RuntimeException {

    private final CrossrefFailureType failureType;
    private final Duration retryAfter;

    public CrossrefApiException(CrossrefFailureType failureType, String message) {
        this(failureType, message, null);
    }

    public CrossrefApiException(CrossrefFailureType failureType, String message, Duration retryAfter) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failureType 不能为空");
        this.retryAfter = retryAfter;
    }

    public CrossrefFailureType getFailureType() { return failureType; }
    public Duration getRetryAfter() { return retryAfter; }
}
