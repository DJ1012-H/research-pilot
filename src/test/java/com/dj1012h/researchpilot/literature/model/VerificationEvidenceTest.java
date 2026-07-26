package com.dj1012h.researchpilot.literature.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationEvidenceTest {

    @Test
    void shouldKeepNormalizedValuesSeparateAndAllowAnUnscoredField() {
        FieldVerificationEvidence field = new FieldVerificationEvidence(
                VerificationField.TITLE,
                "normalized candidate title",
                "normalized evidence title",
                FieldMatchStatus.NOT_EVALUATED,
                null,
                "similarity threshold is reserved for a later phase"
        );
        List<FieldVerificationEvidence> fields = new ArrayList<>(List.of(field));
        VerificationEvidence evidence = new VerificationEvidence("W1", "CROSSREF", fields);
        fields.clear();

        assertThat(evidence.fieldEvidence()).containsExactly(field);
        assertThat(evidence.fieldEvidence().getFirst().candidateNormalizedValue())
                .isNotEqualTo("original candidate title");
    }

    @Test
    void shouldRejectInvalidScoresAndMissingDeterministicExplanations() {
        assertThatThrownBy(() -> new FieldVerificationEvidence(
                VerificationField.DOI, "candidate", "evidence", FieldMatchStatus.MATCH, 1.1, "rule"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldVerificationEvidence(
                VerificationField.DOI, "candidate", "evidence", FieldMatchStatus.MATCH, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
