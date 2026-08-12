package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagEvidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class RagAnswerValidationPipelineTest {

    @Test
    void shouldRejectMalformedUnknownAndLeakingCitations() {
        RagAnswerValidationPipeline pipeline = pipeline();
        RagAnswerInput input = input();

        assertThatThrownBy(() -> pipeline.validate(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"answer\",\"citationIds\":[\"P0\"]}]}"), input))
                .isInstanceOf(RagAnswerValidationException.class)
                .satisfies(error -> assertThat(((RagAnswerValidationException) error).safeCodes())
                        .contains("MALFORMED_CITATION_ID"));
        assertThatThrownBy(() -> pipeline.validate(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"answer\",\"citationIds\":[\"P99\"]}]}"), input))
                .isInstanceOf(RagAnswerValidationException.class)
                .satisfies(error -> assertThat(((RagAnswerValidationException) error).safeCodes())
                        .contains("UNKNOWN_CITATION_ID"));
        assertThatThrownBy(() -> pipeline.validate(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"10.1000/trusted\",\"citationIds\":[\"P1\"]}]}"), input))
                .isInstanceOf(RagAnswerValidationException.class)
                .satisfies(error -> assertThat(((RagAnswerValidationException) error).safeCodes())
                        .contains("PUBLIC_TEXT_NOT_ALLOWED"));
    }

    @Test
    void shouldRejectSchemaExtraFieldsAndAllowOnlySafeStatements() {
        RagAnswerValidationPipeline pipeline = pipeline();
        RagAnswerInput input = input();

        assertThatThrownBy(() -> pipeline.validate(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"answer\",\"citationIds\":[\"P1\"],\"extra\":true}]}"), input))
                .isInstanceOf(RagAnswerValidationException.class)
                .satisfies(error -> assertThat(((RagAnswerValidationException) error).safeCodes())
                        .contains("ADDITIONAL_PROPERTY_NOT_ALLOWED"));
        ValidatedRagAnswer valid = pipeline.validate(
                new UntrustedRagAnswerDraft("{\"statements\":[{\"text\":\"answer\",\"citationIds\":[\"P1\"]}]}"), input);
        assertThat(valid.statements()).hasSize(1);
    }

    private RagAnswerValidationPipeline pipeline() {
        StructuredOutputMapper mapper = new StructuredOutputMapper(new StructuredOutputConfiguration().structuredOutputObjectMapper());
        return new RagAnswerValidationPipeline(
                mapper,
                new RagAnswerDraftSchemaValidator(),
                new RagAnswerDraftMapper(mapper),
                new RagAnswerBusinessValidator(),
                new RagAnswerCitationGuard(),
                new RagAnswerProperties());
    }

    private RagAnswerInput input() {
        TrustedRagEvidence evidence = new TrustedRagEvidence(
                UUID.randomUUID(), 7L, "10.1000/trusted", "Trusted title", List.of("Ada Lovelace"),
                2024, "Trusted venue", 0.9, RagSegmentType.ABSTRACT, 0,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", Instant.now(), "trusted text");
        return new RagAnswerInput("question", List.of(RagAnswerEvidence.from(1, evidence)));
    }
}
