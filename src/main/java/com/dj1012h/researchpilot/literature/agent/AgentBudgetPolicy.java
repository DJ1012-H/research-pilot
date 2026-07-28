package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** The only component that decides whether an action may begin. */
@Component
public class AgentBudgetPolicy {

    private final AgentBudgetProperties properties;
    private final Clock clock;

    public AgentBudgetPolicy(AgentBudgetProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BudgetCheckResult checkBeforeAction(AgentState state, AgentAction action, ActionCost estimatedCost) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        if (state.terminated()) {
            return BudgetCheckResult.denied(state.terminationReason(), "task is already terminal");
        }
        if (!Instant.now(clock).isBefore(state.deadline())) {
            return BudgetCheckResult.denied(TerminationReason.DEADLINE_EXCEEDED, "deadline reached");
        }
        if (!isStateCompatible(state, action)) {
            return BudgetCheckResult.denied(TerminationReason.INVALID_STATE,
                    "action " + action + " is not valid for stage " + state.currentStage());
        }
        if (action.countsAsBusinessStep() && state.businessStepCount() + 1 > properties.getMaxBusinessSteps()) {
            return BudgetCheckResult.denied(TerminationReason.STEP_LIMIT_REACHED, "business step budget exhausted");
        }
        if (action == AgentAction.SEARCH_OPENALEX
                && state.searchRoundCount() + 1 > properties.getMaxSearchRounds()) {
            return BudgetCheckResult.denied(TerminationReason.SEARCH_ROUND_LIMIT_REACHED, "OpenAlex search-round budget exhausted");
        }
        if (action == AgentAction.REFINE_PLAN
                && state.planAdjustmentCount() + 1 > properties.getMaxPlanAdjustments()) {
            return BudgetCheckResult.denied(TerminationReason.PLAN_ADJUSTMENT_LIMIT_REACHED,
                    "plan-adjustment budget exhausted");
        }
        if (state.uniqueCandidateCount() + estimatedCost.uniqueCandidates() > properties.getMaxUniqueCandidates()) {
            return BudgetCheckResult.denied(TerminationReason.CANDIDATE_BUDGET_EXHAUSTED,
                    "global unique-candidate budget would be exceeded");
        }
        if (state.crossrefCallCount() + estimatedCost.crossrefCalls() > properties.getMaxCrossrefCalls()) {
            return BudgetCheckResult.denied(TerminationReason.CROSSREF_BUDGET_EXHAUSTED,
                    "Crossref call budget would be exceeded");
        }
        return BudgetCheckResult.permitted();
    }

    private boolean isStateCompatible(AgentState state, AgentAction action) {
        return switch (action) {
            case CREATE_INITIAL_PLAN -> state.currentStage() == AgentStage.INITIALIZED;
            case SEARCH_OPENALEX, REFINE_PLAN -> state.currentPlan() != null;
            // Detailed transition policy is deliberately deferred. At this stage,
            // a trusted plan is the minimum prerequisite for explicit actions.
            case DEDUPLICATE_CANDIDATES, VERIFY_WITH_CROSSREF, EVALUATE_RESULTS -> state.currentPlan() != null;
            case COMPLETE, TERMINATE -> true;
        };
    }
}
