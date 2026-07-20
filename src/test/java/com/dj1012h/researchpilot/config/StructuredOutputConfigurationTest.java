package com.dj1012h.researchpilot.config;

import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputConfigurationTest {

    private final ObjectMapper objectMapper =
            new StructuredOutputConfiguration().structuredOutputObjectMapper();

    @Test
    void shouldLoadVersionedPromptAndSchema() throws IOException {
        String prompt = new ClassPathResource("prompts/search-plan-v1.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode schema = objectMapper.readTree(
                new ClassPathResource("schema/search-plan-v1.schema.json").getInputStream()
        );

        assertThat(prompt)
                .contains("exactly one complete JSON object")
                .contains("Never generate a URL")
                .contains("search-plan-v1");
        assertThat(schema.path("additionalProperties").booleanValue()).isFalse();
        assertThat(schema.path("required")).hasSize(10);
        assertThat(schema.at("/properties/resultLimit/maximum").intValue()).isEqualTo(50);
        assertThat(schema.at("/properties/languages/items/enum"))
                .extracting(JsonNode::textValue)
                .containsExactly("en", "zh");
    }

    @Test
    void shouldExposeSafeStructuredOutputDefaults() {
        AiProperties.StructuredOutput properties = new AiProperties().getStructuredOutput();

        assertThat(properties.getMaxOutputLength()).isEqualTo(32_768);
        assertThat(properties.getMaxValidationRetries()).isEqualTo(1);
        assertThat(properties.getPromptVersion()).isEqualTo("search-plan-v1");
        assertThat(properties.getSchemaVersion()).isEqualTo("search-plan-v1");
    }

    @Test
    void shouldMapAValidDraft() throws JsonProcessingException {
        SearchPlanDraft draft = objectMapper.readValue(validJson(), SearchPlanDraft.class);

        assertThat(draft.topic()).isEqualTo("remote sensing change detection");
        assertThat(draft.languages()).containsExactly("en");
        assertThat(draft.resultLimit()).isEqualTo(10);
    }

    @Test
    void shouldRejectUnknownFields() {
        String json = validJson().replace(
                "\"resultLimit\": 10",
                "\"resultLimit\": 10, \"reasoning\": \"hidden\""
        );

        assertThatThrownBy(() -> objectMapper.readValue(json, SearchPlanDraft.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("reasoning");
    }

    @Test
    void shouldRejectStringToIntegerCoercion() {
        String json = validJson().replace("\"resultLimit\": 10", "\"resultLimit\": \"10\"");

        assertThatThrownBy(() -> objectMapper.readValue(json, SearchPlanDraft.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("resultLimit");
    }

    @Test
    void shouldRejectSingleStringToArrayCoercion() {
        String json = validJson().replace("\"languages\": [\"en\"]", "\"languages\": \"en\"");

        assertThatThrownBy(() -> objectMapper.readValue(json, SearchPlanDraft.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("languages");
    }

    @Test
    void shouldRejectNumericEnumValues() {
        assertThatThrownBy(() -> objectMapper.readValue("0", LanguageCode.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    private String validJson() {
        return """
                {
                  "topic": "remote sensing change detection",
                  "englishKeywords": ["Mamba", "remote sensing"],
                  "searchQuery": "Mamba remote sensing change detection",
                  "languages": ["en"],
                  "publicationTypes": ["article"],
                  "sort": "newest",
                  "recentYears": 5,
                  "fromYear": null,
                  "toYear": null,
                  "resultLimit": 10
                }
                """;
    }
}
