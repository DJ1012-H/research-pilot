package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPlanValidationPipelineTest {

    private final AiProperties aiProperties = new AiProperties();
    private final LiteratureSearchProperties searchProperties = new LiteratureSearchProperties();
    private final ObjectMapper objectMapper =
            new StructuredOutputConfiguration().structuredOutputObjectMapper();
    private final StructuredOutputMapper structuredOutputMapper =
            new StructuredOutputMapper(objectMapper);

    @Test
    void shouldCreateTrustedPlanUsingRequestThenDraftThenDefaults() {
        SearchRequest request = new SearchRequest(
                "2022 年以后最新的 Mamba 遥感变化检测论文，返回 10 篇",
                2022,
                null,
                10
        );

        SearchPlan plan = pipeline().validate(context(request), validJson());

        assertThat(plan.originalQuery()).isEqualTo(request.query());
        assertThat(plan.fromYear()).isEqualTo(2022);
        assertThat(plan.toYear()).isEqualTo(2025);
        assertThat(plan.resultLimit()).isEqualTo(10);
        assertThat(plan.candidateLimit()).isEqualTo(30);
        assertThat(plan.languages()).containsExactly(LanguageCode.EN);
        assertThat(plan.sort()).isEqualTo(SearchSort.NEWEST);
    }

    @Test
    void shouldStopAtJsonSyntaxStage() {
        assertThatThrownBy(() -> pipeline().validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                "```json\n" + validJson() + "\n```"
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.JSON_SYNTAX);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("INVALID_JSON_SYNTAX");
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void shouldStopAtSchemaBeforeMappingUnknownField() {
        String invalid = validJson().replace(
                "\"resultLimit\": 15",
                "\"resultLimit\": 15, \"reasoning\": \"hidden\""
        );

        assertThatThrownBy(() -> pipeline().validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                invalid
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.JSON_SCHEMA);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .contains("ADDITIONAL_PROPERTY_NOT_ALLOWED");
                });
    }

    @Test
    void shouldRejectConflictingTimeConstraintsAtBusinessStage() {
        String invalid = validJson().replace("\"recentYears\": null", "\"recentYears\": 2");

        assertThatThrownBy(() -> pipeline().validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                invalid
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.BUSINESS_RULE);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("CONFLICTING_TIME_CONSTRAINTS");
                });
    }

    @Test
    void shouldRejectExecutionSyntaxAtNonRetryableSecurityStage() {
        String invalid = validJson().replace(
                "Mamba remote sensing change detection",
                "Mamba https://malicious.example"
        );

        assertThatThrownBy(() -> pipeline().validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                invalid
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.SECURITY);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("SECURITY_VALIDATION_FAILED");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void shouldRejectOversizedOutputWithoutRetry() {
        aiProperties.getStructuredOutput().setMaxOutputLength(5);
        JsonSyntaxValidator validator =
                new JsonSyntaxValidator(structuredOutputMapper, aiProperties);

        assertThatThrownBy(() -> validator.validate("{\"topic\":\"too long\"}"))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.JSON_SYNTAX);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("MODEL_OUTPUT_TOO_LARGE");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    private SearchPlanValidationPipeline pipeline() {
        return new SearchPlanValidationPipeline(
                new JsonSyntaxValidator(structuredOutputMapper, aiProperties),
                new SearchPlanSchemaValidator(aiProperties),
                new SearchPlanDraftMapper(structuredOutputMapper),
                new SearchPlanBusinessValidator(searchProperties),
                new SearchPlanSecurityValidator(searchProperties)
        );
    }

    private SearchPlanGenerationContext context(SearchRequest request) {
        return new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                request,
                Instant.parse("2026-07-20T08:00:00Z"),
                2026
        );
    }

    private String validJson() {
        return """
                {
                  "topic": "Mamba-based remote sensing change detection",
                  "englishKeywords": ["Mamba", "remote sensing", "change detection"],
                  "searchQuery": "Mamba remote sensing change detection",
                  "languages": ["en"],
                  "publicationTypes": ["article", "review"],
                  "sort": "newest",
                  "recentYears": null,
                  "fromYear": 2020,
                  "toYear": 2025,
                  "resultLimit": 15
                }
                """;
    }
}
