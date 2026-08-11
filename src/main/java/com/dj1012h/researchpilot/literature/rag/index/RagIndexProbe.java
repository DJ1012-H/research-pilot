package com.dj1012h.researchpilot.literature.rag.index;

import java.util.Objects;

public record RagIndexProbe(boolean available, String detail) {

    public RagIndexProbe {
        detail = Objects.requireNonNull(detail, "detail must not be null");
        if (detail.isBlank()) throw new IllegalArgumentException("detail must not be blank");
    }
}
