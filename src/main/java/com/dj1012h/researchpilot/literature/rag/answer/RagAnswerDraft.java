package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Strictly mapped but still untrusted model draft. */
public record RagAnswerDraft(List<RagAnswerStatement> statements) {
    public RagAnswerDraft {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements must not be null"));
    }
}
