package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPlanDraftMapperTest {

    private final ObjectMapper objectMapper =
            new StructuredOutputConfiguration().structuredOutputObjectMapper();
    private final SearchPlanDraftMapper mapper = new SearchPlanDraftMapper(objectMapper);

    @Test
    void shouldMapValidJsonTree() throws Exception {
        SearchPlanDraft draft = mapper.map(objectMapper.readTree(validJson()));

        assertThat(draft.topic()).isEqualTo("Mamba remote sensing change detection");
        assertThat(draft.languages()).containsExactly("en");
        assertThat(draft.resultLimit()).isEqualTo(10);
    }

    @Test
    void shouldRejectUnknownFieldEvenWhenSchemaIsBypassed() throws Exception {
        JsonNode root = objectMapper.readTree(validJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("reasoning", "hidden");

        assertMappingFailure(root);
    }

    @Test
    void shouldRejectStringToIntegerCoercion() throws Exception {
        JsonNode root = objectMapper.readTree(validJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("resultLimit", "10");

        assertMappingFailure(root);
    }

    @Test
    void shouldRejectSingleValueToListCoercion() throws Exception {
        JsonNode root = objectMapper.readTree(validJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("languages", "en");

        assertMappingFailure(root);
    }

    private void assertMappingFailure(JsonNode root) {
        assertThatThrownBy(() -> mapper.map(root))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.DTO_MAPPING);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("DTO_MAPPING_FAILED");
                });
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
