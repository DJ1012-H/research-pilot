package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSyntaxValidatorTest {

    private final AiProperties properties = new AiProperties();
    private final JsonSyntaxValidator validator = validator(properties);

    @Test
    void shouldAcceptExactlyOneJsonObject() {
        JsonNode result = validator.validate("{\"topic\":\"Mamba\"}");

        assertThat(result.isObject()).isTrue();
        assertThat(result.path("topic").textValue()).isEqualTo("Mamba");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "\n\t"
    })
    void shouldRejectBlankOutput(String output) {
        assertFailure(output, "EMPTY_MODEL_OUTPUT", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "```json\n{}\n```",
            "{\"topic\":\"Mamba\",}",
            "{\"topic\":\"Mamba\"",
            "{} {}"
    })
    void shouldRejectNonStrictJson(String output) {
        assertFailure(output, "INVALID_JSON_SYNTAX", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "\"text\"",
            "42"
    })
    void shouldRejectNonObjectRoot(String output) {
        assertFailure(output, "JSON_ROOT_NOT_OBJECT", true);
    }

    @Test
    void shouldRejectNullOutput() {
        assertFailure(null, "EMPTY_MODEL_OUTPUT", true);
    }

    @Test
    void shouldRejectOversizedOutputWithoutRetry() {
        AiProperties smallBudget = new AiProperties();
        smallBudget.getStructuredOutput().setMaxOutputLength(5);

        assertThatThrownBy(() -> validator(smallBudget).validate("{\"a\":1}"))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("MODEL_OUTPUT_TOO_LARGE");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    private void assertFailure(String output, String expectedCode, boolean retryable) {
        assertThatThrownBy(() -> validator.validate(output))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.JSON_SYNTAX);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly(expectedCode);
                    assertThat(exception.isRetryable()).isEqualTo(retryable);
                });
    }

    private JsonSyntaxValidator validator(AiProperties aiProperties) {
        return new JsonSyntaxValidator(
                new StructuredOutputConfiguration().structuredOutputObjectMapper(),
                aiProperties
        );
    }
}
