package com.dj1012h.researchpilot.config;

import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SearchConfigurationConsistencyTest {

    private final ObjectMapper objectMapper =
            new StructuredOutputConfiguration().structuredOutputObjectMapper();

    @Test
    void javaAndRuntimeLimitsMustStayAligned() {
        LiteratureSearchProperties properties = new LiteratureSearchProperties();

        assertThat(properties.getMaxResultLimit()).isEqualTo(SearchPlan.MAX_RESULT_LIMIT);
        assertThat(properties.getMaxCandidateLimit()).isEqualTo(SearchPlan.MAX_CANDIDATE_LIMIT);
        assertThat(properties.getEarliestSupportedYear())
                .isEqualTo(SearchPlan.EARLIEST_SUPPORTED_YEAR);
        assertThat(OpenAlexQuery.MAX_PAGE_SIZE)
                .isGreaterThanOrEqualTo(SearchPlan.MAX_CANDIDATE_LIMIT);
    }

    @Test
    void schemaAndJavaLimitsMustStayAligned() throws IOException {
        JsonNode schema = objectMapper.readTree(
                new ClassPathResource("schema/search-plan-v1.schema.json").getInputStream()
        );

        assertThat(schema.at("/properties/resultLimit/maximum").intValue())
                .isEqualTo(SearchPlan.MAX_RESULT_LIMIT);
        assertThat(schema.at("/properties/searchQuery/maxLength").intValue())
                .isEqualTo(SearchPlan.MAX_SEARCH_QUERY_LENGTH);
        assertThat(schema.at("/properties/fromYear/minimum").intValue())
                .isEqualTo(SearchPlan.EARLIEST_SUPPORTED_YEAR);
    }

    @Test
    void promptSchemaAndRetryPolicyMustUseOneVersionedContract() {
        AiProperties.StructuredOutput structuredOutput =
                new AiProperties().getStructuredOutput();

        assertThat(structuredOutput.getPromptVersion())
                .isEqualTo(structuredOutput.getSchemaVersion())
                .isEqualTo("search-plan-v1");
        assertThat(structuredOutput.getMaxValidationRetries()).isOne();
        assertThat(new ClassPathResource(
                "prompts/" + structuredOutput.getPromptVersion() + ".txt"
        ).exists()).isTrue();
        assertThat(new ClassPathResource(
                "schema/" + structuredOutput.getSchemaVersion() + ".schema.json"
        ).exists()).isTrue();
    }
}
