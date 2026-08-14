package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagEvidenceAdmissionValidatorTest {

    private final StructuredOutputMapper mapper = new StructuredOutputMapper(
            new StructuredOutputConfiguration().structuredOutputObjectMapper());
    private final RagEvidenceAdmissionValidator validator = new RagEvidenceAdmissionValidator(mapper);

    @Test
    void shouldAdmitOnlyCurrentRequestEvidenceIdsInModelOrder() {
        RagEvidenceAdmissionDecision decision = validator.validate(
                "{\"relevant\":true,\"admittedEvidenceIds\":[\"P2\",\"P1\"],\"reason\":\"Both abstracts directly support the task.\"}",
                input());

        assertThat(decision.relevant()).isTrue();
        assertThat(decision.admittedEvidenceIds()).containsExactly("P2", "P1");
    }

    @Test
    void shouldAcceptAConsistentRejection() {
        RagEvidenceAdmissionDecision decision = validator.validate(
                "{\"relevant\":false,\"admittedEvidenceIds\":[],\"reason\":\"The domain and requested task do not match.\"}",
                input());

        assertThat(decision.relevant()).isFalse();
        assertThat(decision.admittedEvidenceIds()).isEmpty();
    }

    @Test
    void shouldRejectInvalidJsonUnknownFieldsAndInconsistentState() {
        assertInvalid("not-json");
        assertInvalid("{\"relevant\":false,\"admittedEvidenceIds\":[],\"reason\":\"No match.\",\"extra\":true}");
        assertInvalid("{\"relevant\":true,\"admittedEvidenceIds\":[],\"reason\":\"No match.\"}");
    }

    @Test
    void shouldRejectEvidenceOutsideTheCurrentRequest() {
        assertInvalid("{\"relevant\":true,\"admittedEvidenceIds\":[\"P3\"],\"reason\":\"Claimed match.\"}");
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> validator.validate(raw, input()))
                .isInstanceOf(IllegalArgumentException.class);
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
