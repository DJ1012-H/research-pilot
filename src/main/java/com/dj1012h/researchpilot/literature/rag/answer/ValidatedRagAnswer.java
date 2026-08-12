package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Validation result that contains only safe statement text and owned IDs. */
public record ValidatedRagAnswer(List<RagAnswerStatement> statements) {
    public ValidatedRagAnswer {
        statements = List.copyOf(Objects.requireNonNull(statements, "statements must not be null"));
        if (statements.isEmpty()) throw new IllegalArgumentException("statements must not be empty");
    }
}
