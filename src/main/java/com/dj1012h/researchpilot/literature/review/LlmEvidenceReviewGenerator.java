package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Reuses the shared model boundary without tools, logging, persistence, or public output. */
@Component
public class LlmEvidenceReviewGenerator implements EvidenceReviewGenerator {

    private final ModelInvoker modelInvoker;

    public LlmEvidenceReviewGenerator(ModelInvoker modelInvoker) {
        this.modelInvoker = Objects.requireNonNull(modelInvoker, "modelInvoker must not be null");
    }

    @Override
    public UntrustedReviewDraft generate(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        return new UntrustedReviewDraft(modelInvoker.invoke("evidence_review", prompt));
    }
}
