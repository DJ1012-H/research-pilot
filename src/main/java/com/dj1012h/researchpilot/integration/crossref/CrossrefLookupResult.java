package com.dj1012h.researchpilot.integration.crossref;

import java.util.Objects;

public record CrossrefLookupResult(Status status, CrossrefWorkMetadata metadata) {
    public CrossrefLookupResult {
        status = Objects.requireNonNull(status, "status 不能为空");
        if ((status == Status.FOUND) != (metadata != null)) {
            throw new IllegalArgumentException("FOUND 必须且只能包含元数据");
        }
    }

    public static CrossrefLookupResult found(CrossrefWorkMetadata metadata) {
        return new CrossrefLookupResult(Status.FOUND, Objects.requireNonNull(metadata));
    }

    public static CrossrefLookupResult notFound() { return new CrossrefLookupResult(Status.NOT_FOUND, null); }

    public enum Status { FOUND, NOT_FOUND }
}
