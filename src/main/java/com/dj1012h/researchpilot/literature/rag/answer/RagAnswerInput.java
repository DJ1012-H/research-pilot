package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Internal input shared by prompt, validation and citation assembly. */
public record RagAnswerInput(String question, List<RagAnswerEvidence> evidence) {
    public RagAnswerInput {
        question = Objects.requireNonNull(question, "question must not be null");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        if (question.isBlank()) throw new IllegalArgumentException("question must not be blank");
        if (evidence.isEmpty()) throw new IllegalArgumentException("evidence must not be empty");
    }
}
