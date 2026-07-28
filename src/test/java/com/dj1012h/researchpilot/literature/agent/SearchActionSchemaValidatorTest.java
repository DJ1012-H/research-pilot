package com.dj1012h.researchpilot.literature.agent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchActionSchemaValidatorTest {

    @Test
    void shouldRejectSystemOnlyActionFromTheModelSchema() throws Exception {
        assertThatThrownBy(() -> new SearchActionSchemaValidator().validate(
                JsonMapper.builder().build().readTree("{\"action\":\"CREATE_INITIAL_PLAN\"}")))
                .isInstanceOfSatisfying(SearchActionValidationException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getStage())
                                .isEqualTo(SearchActionValidationStage.JSON_SCHEMA));
    }
}
