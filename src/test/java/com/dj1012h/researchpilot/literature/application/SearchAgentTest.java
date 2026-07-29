package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.SearchConstraintField;
import com.dj1012h.researchpilot.literature.model.SearchConstraintOrigins;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationPipeline;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import com.dj1012h.researchpilot.literature.validation.ValidationStage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchAgentTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC);
    private static final SearchRequest REQUEST =
            new SearchRequest("Mamba 遥感变化检测", null, null, 10);

    private final LlmQueryPlanner planner = mock(LlmQueryPlanner.class);
    private final SearchPlanValidationPipeline pipeline = mock(SearchPlanValidationPipeline.class);
    private final AiProperties aiProperties = new AiProperties();

    @Test
    void shouldReturnFirstValidatedPlanWithoutRetry() {
        SearchPlan expected = plan();
        when(planner.generate(any())).thenReturn("first output");
        when(pipeline.validateWithOrigins(any(), eq("first output")))
                .thenReturn(result(expected));

        SearchPlan actual = agent().createPlan(REQUEST);

        assertThat(actual).isSameAs(expected);
        verify(planner, never()).regenerate(any(), any());
    }

    @Test
    void shouldRetryOnceAndReturnCorrectedPlan() {
        SearchPlanValidationException firstFailure = retryableFailure();
        SearchPlan expected = plan();
        when(planner.generate(any())).thenReturn("first output");
        when(pipeline.validateWithOrigins(any(), eq("first output"))).thenThrow(firstFailure);
        when(planner.regenerate(any(), any())).thenReturn("corrected output");
        when(pipeline.validateWithOrigins(any(), eq("corrected output")))
                .thenReturn(result(expected));

        SearchPlan actual = agent().createPlan(REQUEST);

        assertThat(actual).isSameAs(expected);
        verify(planner).regenerate(any(), org.mockito.ArgumentMatchers.eq(firstFailure.getIssues()));
    }

    @Test
    void shouldExposeFinalValidationFailureAfterSingleRetry() {
        SearchPlanValidationException firstFailure = retryableFailure();
        SearchPlanValidationException finalFailure = new SearchPlanValidationException(
                ValidationStage.BUSINESS_RULE,
                List.of(new ValidationIssue(
                        "QUERY_KEYWORD_MISMATCH",
                        "$.searchQuery",
                        "检索式与关键词不匹配",
                        true
                ))
        );
        when(planner.generate(any())).thenReturn("first output");
        when(pipeline.validateWithOrigins(any(), eq("first output"))).thenThrow(firstFailure);
        when(planner.regenerate(any(), any())).thenReturn("corrected output");
        when(pipeline.validateWithOrigins(any(), eq("corrected output"))).thenThrow(finalFailure);

        assertThatThrownBy(() -> agent().createPlan(REQUEST))
                .isInstanceOfSatisfying(SearchPlanGenerationException.class, exception -> {
                    assertThat(exception.getFinalStage()).isEqualTo(ValidationStage.BUSINESS_RULE);
                    assertThat(exception.getIssues()).isEqualTo(finalFailure.getIssues());
                    assertThat(exception.getCause()).isSameAs(finalFailure);
                });
        verify(planner).regenerate(any(), org.mockito.ArgumentMatchers.eq(firstFailure.getIssues()));
    }

    @Test
    void shouldNotRetryNonRetryableSecurityFailure() {
        SearchPlanValidationException securityFailure = new SearchPlanValidationException(
                ValidationStage.SECURITY,
                List.of(new ValidationIssue(
                        "SECURITY_VALIDATION_FAILED",
                        "$.searchQuery",
                        "检索式包含执行语法",
                        false
                ))
        );
        when(planner.generate(any())).thenReturn("first output");
        when(pipeline.validateWithOrigins(any(), eq("first output"))).thenThrow(securityFailure);

        assertThatThrownBy(() -> agent().createPlan(REQUEST))
                .isInstanceOf(SearchPlanGenerationException.class)
                .hasCause(securityFailure);
        verify(planner, never()).regenerate(any(), any());
    }

    @Test
    void shouldNotRetryModelFailure() {
        ModelInvocationException modelFailure = new ModelInvocationException(
                ModelFailureType.TIMEOUT,
                new RuntimeException("provider details")
        );
        when(planner.generate(any())).thenThrow(modelFailure);

        assertThatThrownBy(() -> agent().createPlan(REQUEST))
                .isSameAs(modelFailure);
        verify(pipeline, never()).validateWithOrigins(any(), any());
        verify(planner, never()).regenerate(any(), any());
    }

    @Test
    void shouldHonorDisabledValidationRetry() {
        aiProperties.getStructuredOutput().setMaxValidationRetries(0);
        SearchPlanValidationException failure = retryableFailure();
        when(planner.generate(any())).thenReturn("first output");
        when(pipeline.validateWithOrigins(any(), eq("first output"))).thenThrow(failure);

        assertThatThrownBy(() -> agent().createPlan(REQUEST))
                .isInstanceOf(SearchPlanGenerationException.class)
                .hasCause(failure);
        verify(planner, never()).regenerate(any(), any());
    }

    private SearchAgent agent() {
        return new SearchAgent(planner, pipeline, aiProperties, FIXED_CLOCK);
    }

    private SearchPlanValidationException retryableFailure() {
        return new SearchPlanValidationException(
                ValidationStage.JSON_SCHEMA,
                List.of(new ValidationIssue(
                        "INVALID_ENUM_VALUE",
                        "$.sort",
                        "排序值不在枚举中",
                        true
                ))
        );
    }

    private SearchPlan plan() {
        return new SearchPlan(
                REQUEST.query(),
                "Mamba remote sensing change detection",
                List.of("Mamba", "remote sensing", "change detection"),
                "Mamba remote sensing change detection",
                Set.of(LanguageCode.EN),
                List.of("article"),
                SearchSort.RELEVANCE,
                2020,
                2026,
                30,
                10
        );
    }

    private SearchPlanValidationResult result(SearchPlan plan) {
        EnumMap<SearchConstraintField, ConstraintOrigin> values =
                new EnumMap<>(SearchConstraintField.class);
        for (SearchConstraintField field : SearchConstraintField.values()) {
            values.put(field, ConstraintOrigin.SYSTEM_FIXED);
        }
        return new SearchPlanValidationResult(plan, new SearchConstraintOrigins(values));
    }
}
