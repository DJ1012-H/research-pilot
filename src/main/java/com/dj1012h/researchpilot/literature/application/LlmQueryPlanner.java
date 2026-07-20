package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Produces untrusted search-plan JSON through the shared model boundary.
 */
@Component
public class LlmQueryPlanner {

    private final ModelInvoker modelInvoker;
    private final SearchPlanPromptBuilder promptBuilder;

    public LlmQueryPlanner(ModelInvoker modelInvoker, SearchPlanPromptBuilder promptBuilder) {
        this.modelInvoker = modelInvoker;
        this.promptBuilder = promptBuilder;
    }

    public String generate(SearchPlanGenerationContext context) {
        Objects.requireNonNull(context, "context 不能为空");
        return modelInvoker.invoke("search_plan", promptBuilder.buildInitial(context));
    }

    public String regenerate(
            SearchPlanGenerationContext context,
            List<ValidationIssue> issues
    ) {
        Objects.requireNonNull(context, "context 不能为空");
        return modelInvoker.invoke("search_plan_retry", promptBuilder.buildRetry(context, issues));
    }
}
