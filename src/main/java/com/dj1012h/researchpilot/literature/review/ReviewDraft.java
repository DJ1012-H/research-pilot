package com.dj1012h.researchpilot.literature.review;

import java.util.List;
import java.util.Objects;

/** Strictly structured model output; trust is established only after CitationGuard. */
public record ReviewDraft(List<ReviewStatement> statements) {
    public ReviewDraft {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements must not be null"));
    }
}
