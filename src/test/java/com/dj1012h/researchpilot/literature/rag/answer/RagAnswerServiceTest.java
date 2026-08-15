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
        assertThat(response.diagnostics().relevanceJudgeCallCount()).isZero();
        assertThat(response.diagnostics().answerModelCallCount()).isZero();
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
        assertThat(response.diagnostics().modelCallCount()).isEqualTo(2);
        assertThat(response.diagnostics().relevanceJudgeCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().answerModelCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().admittedEvidenceCount()).isEqualTo(1);
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
    void shouldExposeFiveGenerationEvidenceItemsWhenSixTrustedAbstractsAreAdmitted() {
        List<TrustedRagEvidence> admittedEvidence = java.util.stream.LongStream.rangeClosed(1, 6)
                .mapToObj(this::boundaryEvidence)
                .toList();
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 10, 6, 6, 6, 0, 1, admittedEvidence, null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        when(generator.generate(anyString())).thenReturn(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"Bounded to five items.\",\"citationIds\":[\"P5\"]}]}"));
        RagAnswerService service = service(true, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(
                new ResearchQuestionRequest("bounded question", 10, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.SUCCESS);
        assertThat(response.retrievalSummary().evidenceCount()).isEqualTo(6);
        assertThat(response.diagnostics().admittedEvidenceCount()).isEqualTo(5);
        assertThat(response.diagnostics().generationEvidenceCount()).isEqualTo(5);
        assertThat(response.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.evidencePosition()).isEqualTo(5));
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(generator).generate(prompt.capture());
        assertThat(prompt.getValue()).contains("\"citationId\":\"P5\"")
                .doesNotContain("\"citationId\":\"P6\"");
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
        assertThat(response.diagnostics().modelCallCount()).isEqualTo(3);
        assertThat(response.diagnostics().relevanceJudgeCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().answerModelCallCount()).isEqualTo(2);
        assertThat(response.diagnostics().repairCount()).isEqualTo(1);
        verify(generator, times(2)).generate(anyString());
    }

    @Test
    void shouldExposeSafeDetailAfterSecondAnswerValidationFailure() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 5, 1, 1, 1, 0, 1, List.of(evidence()), null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        when(generator.generate(anyString())).thenReturn(
                new UntrustedRagAnswerDraft(
                        "{\"statements\":[{\"text\":\"unsupported citation\",\"citationIds\":[\"P99\"]}]}"));
        RagAnswerService service = service(true, retrieval, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(
                new ResearchQuestionRequest("bounded question", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.FAILED);
        assertThat(response.diagnostics().failureCode()).isEqualTo("RAG_ANSWER_VALIDATION_FAILED");
        assertThat(response.diagnostics().failureDetailCode())
                .isEqualTo("RAG_ANSWER_CITATION_GUARD_UNKNOWN_CITATION_ID");
        assertThat(response.diagnostics().answerModelCallCount()).isEqualTo(2);
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
        assertThat(response.diagnostics().modelCallCount()).isEqualTo(2);
        assertThat(response.diagnostics().relevanceJudgeCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().answerModelCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().repairCount()).isZero();
        verify(generator, times(1)).generate(anyString());
    }

    @Test
    void shouldRejectSemanticallyIrrelevantCandidatesWithoutCallingAnswerModel() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 5, 1, 1, 1, 0, 1, List.of(evidence()), null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        RagEvidenceAdmissionOrchestrator admission = mock(RagEvidenceAdmissionOrchestrator.class);
        when(admission.admit(any())).thenReturn(new RagEvidenceAdmissionResult(List.of(), 1));
        RagAnswerService service = service(true, retrieval, admission, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(new ResearchQuestionRequest(
                "Which state-space models predict protein folding structures?", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.answer()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.diagnostics().modelCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().relevanceJudgeCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().answerModelCallCount()).isZero();
        assertThat(response.diagnostics().admittedEvidenceCount()).isZero();
        assertThat(response.diagnostics().generationEvidenceCount()).isZero();
        verify(generator, never()).generate(anyString());
    }

    @Test
    void shouldFailClosedWhenAdmissionOutputIsInvalid() {
        RagRetrievalService retrieval = mock(RagRetrievalService.class);
        when(retrieval.retrieveTrusted(any(), any())).thenReturn(
                new TrustedRagRetrieval("SUCCESS", "test-v1", 5, 1, 1, 1, 0, 1, List.of(evidence()), null));
        LlmRagAnswerGenerator generator = mock(LlmRagAnswerGenerator.class);
        RagEvidenceAdmissionOrchestrator admission = mock(RagEvidenceAdmissionOrchestrator.class);
        when(admission.admit(any())).thenThrow(new RagEvidenceAdmissionException(
                RagAnswerFailureType.RAG_EVIDENCE_ADMISSION_INVALID,
                1,
                "RAG_ADMISSION_SCHEMA_INVALID",
                new IllegalArgumentException("invalid")));
        RagAnswerService service = service(true, retrieval, admission, generator, realPipeline());

        ResearchAnswerResponse response = service.answer(
                new ResearchQuestionRequest("bounded question", 5, null, null, List.of()));

        assertThat(response.status()).isEqualTo(RagAnswerStatus.FAILED);
        assertThat(response.diagnostics().failureCode()).isEqualTo("RAG_EVIDENCE_ADMISSION_INVALID");
        assertThat(response.diagnostics().failureDetailCode()).isEqualTo("RAG_ADMISSION_SCHEMA_INVALID");
        assertThat(response.diagnostics().modelCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().relevanceJudgeCallCount()).isEqualTo(1);
        assertThat(response.diagnostics().answerModelCallCount()).isZero();
        verify(generator, never()).generate(anyString());
    }

    private RagAnswerService service(
            boolean enabled,
            RagRetrievalService retrieval,
            LlmRagAnswerGenerator generator,
            RagAnswerValidationPipeline pipeline
    ) {
        RagEvidenceAdmissionOrchestrator admission = mock(RagEvidenceAdmissionOrchestrator.class);
        when(admission.admit(any())).thenAnswer(invocation -> {
            RagAnswerInput input = invocation.getArgument(0);
            return new RagEvidenceAdmissionResult(input.evidence(), 1);
        });
        return service(enabled, retrieval, admission, generator, pipeline);
    }

    private RagAnswerService service(
            boolean enabled,
            RagRetrievalService retrieval,
            RagEvidenceAdmissionOrchestrator admission,
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
                admission,
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

    private TrustedRagEvidence boundaryEvidence(long paperId) {
        return new TrustedRagEvidence(
                new UUID(0L, paperId),
                paperId,
                "10.1000/boundary-" + paperId,
                "Boundary title " + paperId,
                List.of("Ada Lovelace"),
                2024,
                "Trusted venue",
                1.0 - paperId / 100.0,
                RagSegmentType.ABSTRACT,
                0,
                "%064x".formatted(paperId),
                NOW,
                "Title: Boundary title " + paperId + "\nAbstract: trusted boundary evidence " + paperId);
    }

    private void verifyNoCalls(RagRetrievalService retrieval, LlmRagAnswerGenerator generator) {
        verify(retrieval, never()).retrieveTrusted(any(), any());
        verify(generator, never()).generate(anyString());
    }
}
