package com.dj1012h.researchpilot.integration.openalex;

import java.util.Objects;

public class OpenAlexApiException extends RuntimeException {

    private final OpenAlexFailureType failureType;

    public OpenAlexApiException(OpenAlexFailureType failureType, String safeMessage) {
        super(safeMessage);
        this.failureType = Objects.requireNonNull(failureType, "failureType 不能为空");
    }

    public OpenAlexFailureType getFailureType() {
        return failureType;
    }
}
