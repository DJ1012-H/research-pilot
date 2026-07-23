package com.dj1012h.researchpilot.literature.integration;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.LiteratureSearchService;
import com.dj1012h.researchpilot.literature.application.CrossrefCandidateLookupService;
import com.dj1012h.researchpilot.literature.application.CrossrefLookupSummary;
import com.dj1012h.researchpilot.literature.application.LlmQueryPlanner;
import com.dj1012h.researchpilot.literature.application.OpenAlexQueryFactory;
import com.dj1012h.researchpilot.literature.application.SearchAgent;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationException;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.validation.JsonSyntaxValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanBusinessValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanDraftMapper;
import com.dj1012h.researchpilot.literature.validation.SearchPlanSchemaValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanSecurityValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationPipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LiteratureSearchFlowIntegrationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC);

    private final LlmQueryPlanner planner = mock(LlmQueryPlanner.class);
    private final OpenAlexSearchPort openAlexSearchPort = mock(OpenAlexSearchPort.class);
    private final CrossrefCandidateLookupService crossrefCandidateLookupService = mock(CrossrefCandidateLookupService.class);
    private final AiProperties aiProperties = new AiProperties();

    @Test
    void shouldConvertRawModelStringToTrustedOpenAlexQueryAndSearchOnce() {
        SearchRequest request =
                new SearchRequest("近五年最新的 Mamba 遥感变化检测论文", null, null, 10);
        when(planner.generate(any())).thenReturn(validJson());
        when(openAlexSearchPort.search(any()))
                .thenReturn(new OpenAlexSearchResult(0, List.of(), null));
        when(crossrefCandidateLookupService.lookup(List.of()))
                .thenReturn(new CrossrefLookupSummary(0, 0, 0, 0, 0, 0, 0, false, false, List.of(), List.of()));

        SearchResponse response = service().search(request);

        ArgumentCaptor<OpenAlexQuery> queryCaptor = ArgumentCaptor.forClass(OpenAlexQuery.class);
        verify(openAlexSearchPort).search(queryCaptor.capture());
        OpenAlexQuery query = queryCaptor.getValue();
        assertThat(query.search()).isEqualTo("Mamba remote sensing change detection");
        assertThat(query.fromPublicationDate().getYear()).isEqualTo(2022);
        assertThat(query.toPublicationDate().getYear()).isEqualTo(2026);
        assertThat(query.languages()).containsExactly("en", "zh");
        assertThat(query.workTypes()).containsExactly("article", "review");
        assertThat(query.sort()).isEqualTo(OpenAlexQuery.Sort.NEWEST);
        assertThat(query.perPage()).isEqualTo(30);
        assertThat(response.plan().originalQuery()).isEqualTo(request.query());
        assertThat(response.plan().candidateLimit()).isEqualTo(30);
    }

    @Test
    void shouldRetryMalformedModelOutputOnlyOnceAndNeverCallOpenAlex(CapturedOutput output) {
        SearchRequest request =
                new SearchRequest("SENSITIVE_USER_QUERY_7f13", null, null, 10);
        when(planner.generate(any())).thenReturn("SENSITIVE_MODEL_OUTPUT_9ac4");
        when(planner.regenerate(any(), any())).thenReturn("{still-not-json}");

        assertThatThrownBy(() -> service().search(request))
                .isInstanceOf(SearchPlanGenerationException.class);

        verify(planner).regenerate(any(), any());
        verify(openAlexSearchPort, never()).search(any());
        assertThat(output)
                .doesNotContain("SENSITIVE_USER_QUERY_7f13")
                .doesNotContain("SENSITIVE_MODEL_OUTPUT_9ac4")
                .doesNotContain("{still-not-json}");
    }

    @Test
    void shouldNotRetrySecurityFailureAndNeverCallOpenAlex() {
        when(planner.generate(any())).thenReturn(validJson().replace(
                "Mamba remote sensing change detection",
                "Mamba https://malicious.example"
        ));

        assertThatThrownBy(() -> service().search(
                new SearchRequest("Mamba 遥感变化检测", null, null, 10)
        )).isInstanceOf(SearchPlanGenerationException.class);

        verify(planner, never()).regenerate(any(), any());
        verify(openAlexSearchPort, never()).search(any());
    }

    @Test
    void shouldPropagateModelFailureWithoutStructuredRetryOrOpenAlexCall() {
        ModelInvocationException modelFailure = new ModelInvocationException(
                ModelFailureType.TIMEOUT,
                new RuntimeException("provider timeout")
        );
        when(planner.generate(any())).thenThrow(modelFailure);

        assertThatThrownBy(() -> service().search(
                new SearchRequest("Mamba 遥感变化检测", null, null, 10)
        )).isSameAs(modelFailure);

        verify(planner, never()).regenerate(any(), any());
        verify(openAlexSearchPort, never()).search(any());
    }

    private LiteratureSearchService service() {
        SearchAgent searchAgent = new SearchAgent(
                planner,
                pipeline(),
                aiProperties,
                FIXED_CLOCK
        );
        return new LiteratureSearchService(
                searchAgent,
                new OpenAlexQueryFactory(),
                openAlexSearchPort,
                crossrefCandidateLookupService,
                FIXED_CLOCK
        );
    }

    private SearchPlanValidationPipeline pipeline() {
        ObjectMapper objectMapper =
                new StructuredOutputConfiguration().structuredOutputObjectMapper();
        StructuredOutputMapper structuredOutputMapper = new StructuredOutputMapper(objectMapper);
        LiteratureSearchProperties searchProperties = new LiteratureSearchProperties();
        return new SearchPlanValidationPipeline(
                new JsonSyntaxValidator(structuredOutputMapper, aiProperties),
                new SearchPlanSchemaValidator(aiProperties),
                new SearchPlanDraftMapper(structuredOutputMapper),
                new SearchPlanBusinessValidator(searchProperties),
                new SearchPlanSecurityValidator(searchProperties)
        );
    }

    private String validJson() {
        return """
                {
                  "topic": "Mamba remote sensing change detection",
                  "englishKeywords": ["Mamba", "remote sensing", "change detection"],
                  "searchQuery": "Mamba remote sensing change detection",
                  "languages": ["en", "zh"],
                  "publicationTypes": ["article", "review"],
                  "sort": "newest",
                  "recentYears": 5,
                  "fromYear": null,
                  "toYear": null,
                  "resultLimit": 10
                }
                """;
    }
}
