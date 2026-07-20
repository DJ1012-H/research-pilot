package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPlanSchemaValidatorTest {

    private final ObjectMapper objectMapper =
            new StructuredOutputConfiguration().structuredOutputObjectMapper();
    private final SearchPlanSchemaValidator validator =
            new SearchPlanSchemaValidator(new AiProperties());

    @Test
    void shouldAcceptCompleteContractObject() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validJson());

        assertThat(validator.validate(root)).isSameAs(root);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCases")
    void shouldRejectEverySchemaBoundary(
            String name,
            Consumer<ObjectNode> mutation,
            String expectedCode
    ) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(validJson());
        mutation.accept(root);

        assertThatThrownBy(() -> validator.validate(root))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.JSON_SCHEMA);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .contains(expectedCode);
                });
    }

    private static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of(
                        "missing required field",
                        (Consumer<ObjectNode>) root -> root.remove("topic"),
                        "MISSING_REQUIRED_FIELD"
                ),
                Arguments.of(
                        "reasoning is forbidden",
                        (Consumer<ObjectNode>) root -> root.put("reasoning", "hidden"),
                        "ADDITIONAL_PROPERTY_NOT_ALLOWED"
                ),
                Arguments.of(
                        "OpenAlex URL is forbidden",
                        (Consumer<ObjectNode>) root -> root.put("openAlexUrl", "https://example.test"),
                        "ADDITIONAL_PROPERTY_NOT_ALLOWED"
                ),
                Arguments.of(
                        "year cannot be a string",
                        (Consumer<ObjectNode>) root -> root.put("fromYear", "2022"),
                        "INVALID_FIELD_TYPE"
                ),
                Arguments.of(
                        "unsupported language",
                        (Consumer<ObjectNode>) root -> root.set("languages", array("fr")),
                        "INVALID_ENUM_VALUE"
                ),
                Arguments.of(
                        "natural language name is unsupported",
                        (Consumer<ObjectNode>) root -> root.set("languages", array("English")),
                        "INVALID_ENUM_VALUE"
                ),
                Arguments.of(
                        "result limit over 50",
                        (Consumer<ObjectNode>) root -> root.put("resultLimit", 51),
                        "SCHEMA_VALIDATION_FAILED"
                ),
                Arguments.of(
                        "unsupported sort",
                        (Consumer<ObjectNode>) root -> root.put("sort", "popular"),
                        "INVALID_ENUM_VALUE"
                ),
                Arguments.of(
                        "unsupported publication type",
                        (Consumer<ObjectNode>) root -> root.set("publicationTypes", array("conference-paper")),
                        "INVALID_ENUM_VALUE"
                ),
                Arguments.of(
                        "relative years cannot be text",
                        (Consumer<ObjectNode>) root -> root.put("recentYears", "five"),
                        "INVALID_FIELD_TYPE"
                )
        );
    }

    private static ArrayNode array(String value) {
        return new ObjectMapper().createArrayNode().add(value);
    }

    private String validJson() {
        return """
                {
                  "topic": "Mamba remote sensing change detection",
                  "englishKeywords": ["Mamba", "remote sensing"],
                  "searchQuery": "Mamba remote sensing change detection",
                  "languages": ["en"],
                  "publicationTypes": ["article"],
                  "sort": "newest",
                  "recentYears": null,
                  "fromYear": 2022,
                  "toYear": 2026,
                  "resultLimit": 10
                }
                """;
    }
}
