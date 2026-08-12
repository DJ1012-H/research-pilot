package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Model-proposed text and citation IDs; no bibliographic fields are accepted. */
public record RagAnswerStatement(String text, List<String> citationIds) {
    public RagAnswerStatement {
        text = Objects.requireNonNull(text, "text must not be null");
        citationIds = List.copyOf(Objects.requireNonNull(citationIds, "citationIds must not be null"));
    }
}
