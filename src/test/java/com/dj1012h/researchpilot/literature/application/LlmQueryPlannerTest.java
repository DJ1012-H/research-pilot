package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmQueryPlannerTest {

    private final ModelInvoker modelInvoker = mock(ModelInvoker.class);
    private final SearchPlanPromptBuilder promptBuilder = mock(SearchPlanPromptBuilder.class);
    private final LlmQueryPlanner planner = new LlmQueryPlanner(modelInvoker, promptBuilder);

    @Test
    void shouldInvokeInitialPlanningOperation() {
        SearchPlanGenerationContext context = context();
        when(promptBuilder.buildInitial(context)).thenReturn("initial prompt");
        when(modelInvoker.invoke("search_plan", "initial prompt")).thenReturn("{\"topic\":\"Mamba\"}");

        String output = planner.generate(context);

        assertThat(output).isEqualTo("{\"topic\":\"Mamba\"}");
        verify(modelInvoker).invoke("search_plan", "initial prompt");
    }

    @Test
    void shouldInvokeRetryOperationWithValidationIssues() {
        SearchPlanGenerationContext context = context();
        List<ValidationIssue> issues = List.of(
                new ValidationIssue("INVALID_SORT", "$.sort", "排序值不受支持", true)
        );
        when(promptBuilder.buildRetry(context, issues)).thenReturn("retry prompt");
        when(modelInvoker.invoke("search_plan_retry", "retry prompt")).thenReturn("{\"sort\":\"newest\"}");

        String output = planner.regenerate(context, issues);

        assertThat(output).isEqualTo("{\"sort\":\"newest\"}");
        verify(modelInvoker).invoke("search_plan_retry", "retry prompt");
    }

    private SearchPlanGenerationContext context() {
        return new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new SearchRequest("Mamba 遥感变化检测", null, null, null),
                Instant.parse("2026-07-20T08:00:00Z"),
                2026
        );
    }
}
