package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.common.ai.ModelInvocationResult;
import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmRagEvidenceRelevanceJudgeTest {

    @Test
    void shouldUseOneBoundedProviderJsonModeInvocation() {
        assertThat(LlmRagEvidenceRelevanceJudge.MAX_OUTPUT_TOKENS).isEqualTo(2_000);
        ModelInvoker invoker = mock(ModelInvoker.class);
        when(invoker.invokeJsonWithUsage(
                "rag_evidence_relevance", "JSON prompt", LlmRagEvidenceRelevanceJudge.MAX_OUTPUT_TOKENS))
                .thenReturn(new ModelInvocationResult("{\"relevant\":false}", 10, 4, 14));

        String result = new LlmRagEvidenceRelevanceJudge(invoker).judge("JSON prompt");

        assertThat(result).isEqualTo("{\"relevant\":false}");
        verify(invoker).invokeJsonWithUsage(
                "rag_evidence_relevance", "JSON prompt", LlmRagEvidenceRelevanceJudge.MAX_OUTPUT_TOKENS);
    }
}
