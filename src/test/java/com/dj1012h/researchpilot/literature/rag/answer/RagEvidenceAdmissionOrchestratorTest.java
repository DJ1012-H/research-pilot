package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvidenceAdmissionOrchestratorTest {

    @Test
    void shouldCallJudgeExactlyOnceAndMapOnlyAdmittedEvidence() {
        LlmRagEvidenceRelevanceJudge judge = mock(LlmRagEvidenceRelevanceJudge.class);
        when(judge.judge(anyString())).thenReturn(
                "{\"relevant\":true,\"admittedEvidenceIds\":[\"P2\"],\"reason\":\"Direct task match.\"}");
        RagEvidenceAdmissionOrchestrator orchestrator = orchestrator(judge);

        RagEvidenceAdmissionResult result = orchestrator.admit(input());

        assertThat(result.relevanceJudgeCallCount()).isEqualTo(1);
        assertThat(result.admittedEvidence()).singleElement()
                .satisfies(evidence -> assertThat(evidence.citationId()).isEqualTo("P2"));
        verify(judge, times(1)).judge(anyString());
    }

    @Test
    void shouldFailClosedAfterOneInvalidJudgeOutput() {
        LlmRagEvidenceRelevanceJudge judge = mock(LlmRagEvidenceRelevanceJudge.class);
        when(judge.judge(anyString())).thenReturn("{bad-json");
        RagEvidenceAdmissionOrchestrator orchestrator = orchestrator(judge);

        assertThatThrownBy(() -> orchestrator.admit(input()))
                .isInstanceOfSatisfying(RagEvidenceAdmissionException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(
                            RagAnswerFailureType.RAG_EVIDENCE_ADMISSION_INVALID);
                    assertThat(exception.relevanceJudgeCallCount()).isEqualTo(1);
                });
        verify(judge, times(1)).judge(anyString());
    }

    @Test
    void shouldFailClosedAfterOneJudgeException() {
        LlmRagEvidenceRelevanceJudge judge = mock(LlmRagEvidenceRelevanceJudge.class);
        when(judge.judge(anyString())).thenThrow(new IllegalStateException("provider detail"));
        RagEvidenceAdmissionOrchestrator orchestrator = orchestrator(judge);

        assertThatThrownBy(() -> orchestrator.admit(input()))
                .isInstanceOfSatisfying(RagEvidenceAdmissionException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(
                            RagAnswerFailureType.RAG_RELEVANCE_JUDGE_UNAVAILABLE);
                    assertThat(exception.relevanceJudgeCallCount()).isEqualTo(1);
                });
        verify(judge, times(1)).judge(anyString());
    }

    private RagEvidenceAdmissionOrchestrator orchestrator(LlmRagEvidenceRelevanceJudge judge) {
        RagAnswerProperties properties = new RagAnswerProperties();
        StructuredOutputMapper mapper = new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper());
        return new RagEvidenceAdmissionOrchestrator(
                new RagEvidenceAdmissionPromptBuilder(mapper, properties),
                judge,
                new RagEvidenceAdmissionValidator(mapper));
    }

    private RagAnswerInput input() {
        return new RagAnswerInput("specific remote-sensing question", List.of(evidence(1), evidence(2)));
    }

    private RagAnswerEvidence evidence(int position) {
        return new RagAnswerEvidence(
                "P" + position,
                position,
                position,
                "10.1000/admission-" + position,
                "Admission title " + position,
                List.of("Ada Lovelace"),
                2024,
                "Trusted venue",
                0.9 - position / 100.0,
                RagSegmentType.ABSTRACT,
                0,
                "%064x".formatted(position),
                Instant.parse("2026-08-13T00:00:00Z"),
                "Title: Admission title " + position + "\nAbstract: trusted evidence " + position);
    }
}
