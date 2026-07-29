package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;

import java.util.Objects;

/** Internal immutable context that keeps trusted plan provenance beside the Agent state. */
public record AgentExecutionContext(
        AgentState state,
        ValidatedSearchPlanContext validatedPlanContext,
        SearchPlanRefinementResult lastRefinementResult,
        CandidateDeduplicationResult currentRoundDeduplication
) {
    public AgentExecutionContext {
        state = Objects.requireNonNull(state, "state must not be null");
        validatedPlanContext = Objects.requireNonNull(
                validatedPlanContext, "validatedPlanContext must not be null");
        currentRoundDeduplication = Objects.requireNonNull(
                currentRoundDeduplication, "currentRoundDeduplication must not be null");
    }

    public static AgentExecutionContext initial(
            AgentState state,
            ValidatedSearchPlanContext validatedPlanContext
    ) {
        return new AgentExecutionContext(
                state, validatedPlanContext, null, CandidateDeduplicationResult.empty());
    }

    public boolean planConsistent() {
        return Objects.equals(state.currentPlan(), validatedPlanContext.validationResult().plan());
    }

    public AgentExecutionContext withState(AgentState nextState) {
        return new AgentExecutionContext(
                nextState, validatedPlanContext, lastRefinementResult, currentRoundDeduplication);
    }
}
