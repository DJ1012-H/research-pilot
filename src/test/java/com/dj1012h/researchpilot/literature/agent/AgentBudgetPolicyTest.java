package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentBudgetPolicyTest {

    private static final Instant START = Instant.parse("2026-07-28T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    @Test
    void shouldEnforceAllCounterBudgetsBeforeAction() {
        AgentState planReady = planReady();
        AgentState twoSearches = planReady
                .startAction(new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()))
                .startAction(new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()));
        AgentState oneAdjustment = planReady.startAction(new ActionExecutionPermit(AgentAction.REFINE_PLAN, ActionCost.none()));
        AgentState eightSteps = planReady;
        for (int index = 0; index < 8; index++) {
            eightSteps = eightSteps.startAction(new ActionExecutionPermit(AgentAction.EVALUATE_RESULTS, ActionCost.none()));
        }
        AgentState fortyFiveCrossref = planReady
                .startAction(new ActionExecutionPermit(AgentAction.VERIFY_WITH_CROSSREF, new ActionCost(0, 45)))
                .recordObservation(observation(45), new ActionExecutionPermit(AgentAction.VERIFY_WITH_CROSSREF, new ActionCost(0, 45)));

        assertThat(policy(CLOCK).checkBeforeAction(twoSearches, AgentAction.SEARCH_OPENALEX, ActionCost.none()).reason())
                .isEqualTo(TerminationReason.SEARCH_ROUND_LIMIT_REACHED);
        assertThat(policy(CLOCK).checkBeforeAction(oneAdjustment, AgentAction.REFINE_PLAN, ActionCost.none()).reason())
                .isEqualTo(TerminationReason.PLAN_ADJUSTMENT_LIMIT_REACHED);
        assertThat(policy(CLOCK).checkBeforeAction(eightSteps, AgentAction.EVALUATE_RESULTS, ActionCost.none()).reason())
                .isEqualTo(TerminationReason.STEP_LIMIT_REACHED);
        assertThat(policy(CLOCK).checkBeforeAction(planReady, AgentAction.DEDUPLICATE_CANDIDATES, new ActionCost(46, 0)).reason())
                .isEqualTo(TerminationReason.CANDIDATE_BUDGET_EXHAUSTED);
        assertThat(policy(CLOCK).checkBeforeAction(fortyFiveCrossref, AgentAction.VERIFY_WITH_CROSSREF, new ActionCost(0, 1)).reason())
                .isEqualTo(TerminationReason.CROSSREF_BUDGET_EXHAUSTED);
        assertThat(policy(CLOCK).checkBeforeAction(planReady, AgentAction.VERIFY_WITH_CROSSREF, new ActionCost(0, 46)).reason())
                .isEqualTo(TerminationReason.CROSSREF_BUDGET_EXHAUSTED);
    }

    @Test
    void shouldTreatDeadlineEqualityAndPriorTerminationAsDeniedWithoutOverwritingReason() {
        AgentState state = planReady();
        AgentBudgetPolicy deadlinePolicy = policy(Clock.fixed(START.plusSeconds(90), ZoneOffset.UTC));
        assertThat(deadlinePolicy.checkBeforeAction(state, AgentAction.SEARCH_OPENALEX, ActionCost.none()).reason())
                .isEqualTo(TerminationReason.DEADLINE_EXCEEDED);

        AgentState terminated = state.terminate(TerminationReason.CANDIDATE_BUDGET_EXHAUSTED, "limit", START);
        assertThat(policy(CLOCK).checkBeforeAction(terminated, AgentAction.SEARCH_OPENALEX, ActionCost.none()).reason())
                .isEqualTo(TerminationReason.CANDIDATE_BUDGET_EXHAUSTED);
    }

    @Test
    void shouldRejectInvalidBudgetConfiguration() {
        AgentBudgetProperties properties = new AgentBudgetProperties();
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxSearchRounds(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxPlanAdjustments(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxBusinessSteps(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxUniqueCandidates(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxCrossrefCalls(0));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTotalTimeout(Duration.ZERO));
    }

    private AgentBudgetPolicy policy(Clock clock) { return new AgentBudgetPolicy(new AgentBudgetProperties(), clock); }
    private AgentState planReady() {
        return AgentState.initialize("query", 10, CLOCK, Duration.ofSeconds(90)).recordInitialPlan(new SearchPlan(
                "query", "topic", List.of("keyword"), "keyword", Set.of(LanguageCode.EN), List.of("article"),
                SearchSort.RELEVANCE, 2020, 2026, 20, 10));
    }
    private AgentObservation observation(int calls) {
        return new AgentObservation(AgentAction.VERIFY_WITH_CROSSREF, AgentStage.VERIFYING,
                AgentStage.VERIFICATION_COMPLETED, true, 0, 0, 0, 0, calls, Duration.ZERO,
                "verification complete", null, START);
    }
}
