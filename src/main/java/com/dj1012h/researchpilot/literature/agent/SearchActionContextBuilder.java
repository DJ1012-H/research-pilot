package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Component
public class SearchActionContextBuilder {

    private final AgentBudgetProperties budgetProperties;
    private final Clock clock;

    public SearchActionContextBuilder(AgentBudgetProperties budgetProperties, Clock clock) {
        this.budgetProperties = Objects.requireNonNull(budgetProperties, "budgetProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SearchActionContext build(AgentState state, Set<AgentAction> allowedActions) {
        Objects.requireNonNull(state, "state must not be null");
        String observation = state.observations().isEmpty() ? null
                : summarize(state.observations().getLast());
        return new SearchActionContext(
                state.currentStage(), state.requestedCount(), state.verifiedPapers().size(),
                state.retrievedCandidates().size(), state.deduplicatedCandidates().size(), state.searchRoundCount(),
                remaining(budgetProperties.getMaxSearchRounds(), state.searchRoundCount()), state.planAdjustmentCount(),
                remaining(budgetProperties.getMaxPlanAdjustments(), state.planAdjustmentCount()), state.businessStepCount(),
                remaining(budgetProperties.getMaxBusinessSteps(), state.businessStepCount()), state.crossrefCallCount(),
                remaining(budgetProperties.getMaxCrossrefCalls(), state.crossrefCallCount()),
                !Instant.now(clock).isBefore(state.deadline()), allowedActions, observation);
    }

    private static int remaining(int maximum, int consumed) {
        return Math.max(0, maximum - consumed);
    }

    private static String summarize(AgentObservation observation) {
        String value = observation.failureCode() == null ? observation.summary() : observation.failureCode();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }
}
