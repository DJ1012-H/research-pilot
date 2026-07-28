package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchActionDeciderTest {

    private static final Instant START = Instant.parse("2026-07-29T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    @Test
    void shouldUseNoModelForAUniqueExecutableActionAndLeaveStateUntouched() {
        AtomicInteger calls = new AtomicInteger();
        AgentState state = state(AgentStage.PLAN_READY, 0);

        SearchActionDecision decision = decider(context -> {
            calls.incrementAndGet();
            return "{\"action\":\"COMPLETE\"}";
        }, CLOCK).decide(state);

        assertThat(decision).isEqualTo(new SearchActionDecision(AgentAction.SEARCH_OPENALEX,
                ActionDecisionSource.POLICY_SINGLE_ACTION));
        assertThat(calls).hasValue(0);
        assertThat(state.currentStage()).isEqualTo(AgentStage.PLAN_READY);
        assertThat(state.businessStepCount()).isZero();
    }

    @Test
    void shouldAcceptValidModelActionAndFallbackForInvalidOutputOrModelFailure() {
        AgentState state = state(AgentStage.EVALUATING_RESULTS, 0);

        assertThat(decider(context -> "{\"action\":\"REFINE_PLAN\"}", CLOCK).decide(state))
                .isEqualTo(new SearchActionDecision(AgentAction.REFINE_PLAN, ActionDecisionSource.MODEL));
        assertThat(decider(context -> "{\"action\":\"VERIFY_WITH_CROSSREF\"}", CLOCK).decide(state).source())
                .isEqualTo(ActionDecisionSource.DETERMINISTIC_FALLBACK);
        assertThat(decider(context -> "{\"action\":\"REFINE_PLAN\",\"maxSearchRounds\":100}", CLOCK)
                .decide(state).source()).isEqualTo(ActionDecisionSource.DETERMINISTIC_FALLBACK);
        assertThat(decider(context -> "not json", CLOCK).decide(state).source())
                .isEqualTo(ActionDecisionSource.DETERMINISTIC_FALLBACK);
        assertThat(decider(context -> { throw new ModelNotConfiguredException("disabled"); }, CLOCK).decide(state).source())
                .isEqualTo(ActionDecisionSource.DETERMINISTIC_FALLBACK);
        assertThat(decider(context -> { throw new ModelInvocationException(ModelFailureType.TIMEOUT, new RuntimeException()); }, CLOCK)
                .decide(state).source()).isEqualTo(ActionDecisionSource.DETERMINISTIC_FALLBACK);
    }

    @Test
    void shouldNeverCallModelWhenBudgetOrDeadlineLeavesNoExecutableAction() {
        AtomicInteger calls = new AtomicInteger();
        SearchActionGenerator generator = context -> { calls.incrementAndGet(); return "{\"action\":\"REFINE_PLAN\"}"; };

        assertThatThrownBy(() -> decider(generator, Clock.fixed(START.plusSeconds(90), ZoneOffset.UTC))
                .decide(state(AgentStage.PLAN_READY, 0)))
                .isInstanceOfSatisfying(SearchActionDecisionUnavailableException.class, exception ->
                        assertThat(exception.getTerminationReason()).isEqualTo(TerminationReason.DEADLINE_EXCEEDED));
        assertThat(calls).hasValue(0);

        SearchActionDecision decision = decider(generator, CLOCK).decide(state(AgentStage.EVALUATING_RESULTS, 1));
        assertThat(decision).isEqualTo(new SearchActionDecision(AgentAction.COMPLETE,
                ActionDecisionSource.POLICY_SINGLE_ACTION));
        assertThat(calls).hasValue(0);
    }

    @Test
    void shouldNotMaskProgramDefectsOrPermitRepeatedTermination() {
        AgentState state = state(AgentStage.EVALUATING_RESULTS, 0);
        assertThatIllegalStateException().isThrownBy(() -> decider(context -> {
            throw new IllegalStateException("defect");
        }, CLOCK).decide(state));

        AgentState terminated = state.terminate(TerminationReason.DEADLINE_EXCEEDED, "deadline", START);
        assertThatIllegalStateException().isThrownBy(() -> decider(context -> "{}", CLOCK).decide(terminated));
    }

    private SearchActionDecider decider(SearchActionGenerator generator, Clock clock) {
        AgentBudgetProperties properties = new AgentBudgetProperties();
        StructuredOutputMapper mapper = new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper());
        return new SearchActionDecider(new AgentTransitionPolicy(), new AgentBudgetPolicy(properties, clock),
                new SearchActionCostEstimator(properties), generator,
                new SearchActionValidationPipeline(mapper, new SearchActionSchemaValidator(),
                        new SearchActionDraftMapper(mapper), new SearchActionBusinessValidator(), new SearchActionSecurityValidator()),
                new SearchActionContextBuilder(properties, clock), new DeterministicSearchActionPolicy());
    }

    private AgentState state(AgentStage stage, int planAdjustments) {
        AgentState initial = AgentState.initialize("private user query", 2, CLOCK, Duration.ofSeconds(90));
        return new AgentState("private user query", 2, plan(), List.of(plan()), stage, null,
                List.of(), List.of(), List.of(), List.of(), 0, planAdjustments, 0, 0, 0, Set.of(), 0,
                List.of(), initial.startedAt(), initial.deadline(), null, null, null);
    }

    private SearchPlan plan() {
        return new SearchPlan("private user query", "topic", List.of("keyword"), "keyword", Set.of(LanguageCode.EN),
                List.of("article"), SearchSort.RELEVANCE, 2020, 2026, 2, 2);
    }
}
