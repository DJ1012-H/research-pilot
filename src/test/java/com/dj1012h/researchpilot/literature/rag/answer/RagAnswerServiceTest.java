package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalProperties;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalRequest;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalService;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagEvidence;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagRetrieval;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagAnswerServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void shouldRemainDisabledWithoutCallingTrustedRetrievalOrModel() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        RagAnswerService service = service(false, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(new ResearchQuestionRequest("bounded question", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.FAILED);
        assertThat(response.diagnostics().failureCode()).isEqualTo("RAG_ANSWER_DISABLED");
        verifyNoCalls(retrieval, generator);
    }

    @Test
    void shouldReturnInsufficientEvidenceWithZeroModelCalls() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("NO_TRUSTED_RESULTS", "test-v1", 5, 2, 2, 0, 2, 1, List.of(), "RAG_NO_TRUSTED_RESULTS"));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        RagAnswerService service = service(true, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(new ResearchQuestionRequest("bounded question", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.answer()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.insufficientEvidence()).isTrue();
        assertThat(response.diagnostics().modelCallCount()).isZero();
        verify(generator, never()).generate(anyString());
    }

    @Test
    void shouldForceAbstractEvidenceAndAssembleCitationFromTrustedJavaEvidence() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 5, 1, 1, 1, 0, 1, List.of(evidence()), null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        when(generator.generate(anyString())).thenReturn(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"该方法使用时序特征建模变化过程。\",\"citationIds\":[\"P1\"]}] }"));
        RagAnswerService service = service(true, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(new ResearchQuestionRequest(
                "  状态空间模型  如何使用？ ", 5, 2021, 2026, List.of(7L)));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.SUCCESS);
        assertThat(response.answer()).contains("时序特征");
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.citationId()).isEqualTo("P1");
            assertThat(citation.paperId()).isEqualTo(7L);
            assertThat(citation.title()).isEqualTo("Trusted title");
            assertThat(citation.normalizedDoi()).isEqualTo("10.1000/trusted");
            assertThat(citation.segmentType()).isEqualTo(RagSegmentType.ABSTRACT);
        });
        var request = org.mockito.ArgumentCaptor.forClass(RagRetrievalRequest.class);
        var forced = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(retrieval).retrieveTrusted(request.capture(), forced.capture());
        assertThat(request.getValue().segmentTypes()).isEmpty();
        assertThat(forced.getValue()).containsExactly(RagSegmentType.ABSTRACT);
        verify(generator, times(1)).generate(anyString());
    }

    @Test
    void shouldRepairOnceAndRevalidateTheCompleteDraft() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 5, 1, 1, 1, 0, 1, List.of(evidence()), null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        when(generator.generate(anyString()))
                .thenReturn(new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"不安全草稿\",\"citationIds\":[\"P99\"]}]}"))
                .thenReturn(new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"修正后的摘要级回答。\",\"citationIds\":[\"P1\"]}]}"));
        RagAnswerService service = service(true, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(new ResearchQuestionRequest("bounded question", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.SUCCESS);
        assertThat(response.answer()).isEqualTo("修正后的摘要级回答。");
        assertThat(response.diagnostics().modelCallCount()).isEqualTo(2);
        assertThat(response.diagnostics().repairCount()).isEqualTo(1);
        verify(generator, times(2)).generate(anyString());
    }

    @Test
    void shouldNotRepairProviderFailure() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 5, 1, 1, 1, 0, 1, List.of(evidence()), null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        when(generator.generate(anyString())).thenThrow(new IllegalStateException("provider detail"));
        RagAnswerService service = service(true, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(new ResearchQuestionRequest("bounded question", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.FAILED);
        assertThat(response.diagnostics().failureCode()).isEqualTo("RAG_GENERATION_UNAVAILABLE");
        assertThat(response.diagnostics().repairCount()).isZero();
        verify(generator, times(1)).generate(anyString());
    }

    private RagAnswerService service(
            boolean enabled,
            RagRetrievalService retrieval,
            LlmRagAnswerGenerator generator,
            RagAnswerValidationPipeline pipeline
    ) {
        RagAnswerProperties answerProperties = new RagAnswerProperties();
        answerProperties.setEnabled(enabled);
        RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setEnabled(true);
        StructuredOutputMapper mapper = new StructuredOutputMapper(new StructuredOutputConfiguration().structuredOutputObjectMapper());
        RagAnswerPromptBuilder promptBuilder = new RagAnswerPromptBuilder(mapper, answerProperties);
        return new RagAnswerService(
                answerProperties,
                retrievalProperties,
                retrieval,
                promptBuilder,
                new RagAnswerRepairPromptBuilder(promptBuilder, answerProperties),
                generator,
                pipeline,
                new RagAnswerResponseAssembler(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RagAnswerValidationPipeline realPipeline() {
        StructuredOutputMapper mapper = new StructuredOutputMapper(new StructuredOutputConfiguration().structuredOutputObjectMapper());
        RagAnswerProperties properties = new RagAnswerProperties();
        return new RagAnswerValidationPipeline(
                mapper,
                new RagAnswerDraftSchemaValidator(),
                new RagAnswerDraftMapper(mapper),
                new RagAnswerBusinessValidator(),
                new RagAnswerCitationGuard(),
                properties);
    }

    private TrustedRagEvidence evidence() {
        return new TrustedRagEvidence(
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                7L,
                "10.1000/trusted",
                "Trusted title",
                List.of("Ada Lovelace"),
                2024,
                "Trusted venue",
                0.91,
                RagSegmentType.ABSTRACT,
                0,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                NOW,
                "Title: Trusted title\nAbstract: trusted reconstructed evidence");
    }

    private void verifyNoCalls(RagRetrievalService retrieval, LlmRagAnswerGenerator generator) {
        verify(retrieval, never()).retrieveTrusted(any(), any());
        verify(generator, never()).generate(anyString());
    }
}
