package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Reuses the shared model boundary and never registers executable tools. */
@Component
public class LlmSearchPlanRefinementGenerator implements SearchPlanRefinementGenerator {

    private final ModelInvoker modelInvoker;
    private final SearchPlanRefinementPromptBuilder promptBuilder;

    public LlmSearchPlanRefinementGenerator(
            ModelInvoker modelInvoker,
            SearchPlanRefinementPromptBuilder promptBuilder
    ) {
        this.modelInvoker = Objects.requireNonNull(modelInvoker, "modelInvoker must not be null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
    }

    @Override
    public String generate(SearchPlanRefinementContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return modelInvoker.invoke(
                "search_plan_refinement",
                promptBuilder.build(context)
        );
    }
}
