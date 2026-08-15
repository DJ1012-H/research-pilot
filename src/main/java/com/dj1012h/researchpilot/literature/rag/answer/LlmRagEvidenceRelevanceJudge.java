package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import com.dj1012h.researchpilot.common.ai.ModelInvocationResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Single, tool-free relevance judgment through the shared configured model boundary. */
@Component
public class LlmRagEvidenceRelevanceJudge {
    static final int MAX_OUTPUT_TOKENS = 2_000;
    private final ModelInvoker modelInvoker;

    public LlmRagEvidenceRelevanceJudge(ModelInvoker modelInvoker) {
        this.modelInvoker = Objects.requireNonNull(modelInvoker, "modelInvoker must not be null");
    }

    public String judge(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        ModelInvocationResult result = modelInvoker.invokeJsonWithUsage(
                "rag_evidence_relevance", prompt, MAX_OUTPUT_TOKENS);
        return result.content();
    }
}
