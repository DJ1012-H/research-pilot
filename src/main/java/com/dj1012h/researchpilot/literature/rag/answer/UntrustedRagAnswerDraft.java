package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.Objects;

/** Raw model output. It must pass the complete validation pipeline before use. */
public record UntrustedRagAnswerDraft(String rawContent) {
    public UntrustedRagAnswerDraft {
        rawContent = Objects.requireNonNull(rawContent, "rawContent must not be null");
    }
}
