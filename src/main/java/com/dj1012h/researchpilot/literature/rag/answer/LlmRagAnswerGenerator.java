package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Uses the existing OpenAI-compatible model through the shared safe boundary. */
@Component
public class LlmRagAnswerGenerator {
    private final ModelInvoker modelInvoker;

    public LlmRagAnswerGenerator(ModelInvoker modelInvoker) {
        this.modelInvoker = Objects.requireNonNull(modelInvoker, "modelInvoker must not be null");
    }

    public UntrustedRagAnswerDraft generate(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        return new UntrustedRagAnswerDraft(modelInvoker.invoke("rag_answer", prompt));
    }
}
