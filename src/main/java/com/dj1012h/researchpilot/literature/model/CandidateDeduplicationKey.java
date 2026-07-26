package com.dj1012h.researchpilot.literature.model;

import java.util.Objects;

public record CandidateDeduplicationKey(
        DeduplicationKeyType type,
        String value
) {

    public CandidateDeduplicationKey {
        type = Objects.requireNonNull(type, "type must not be null");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return type.name() + ":" + value;
    }
}
