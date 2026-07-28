package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTransitionPolicyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);
    private final AgentTransitionPolicy policy = new AgentTransitionPolicy();

    @Test
    void shouldWhitelistOnlyStructuralNextActionsAndKeepResultImmutable() {
        assertThat(policy.allowedActions(state(AgentStage.INITIALIZED))).containsExactly(AgentAction.CREATE_INITIAL_PLAN);
        assertThat(policy.allowedActions(state(AgentStage.PLAN_READY))).containsExactly(AgentAction.SEARCH_OPENALEX);
        assertThat(policy.allowedActions(state(AgentStage.SEARCHING))).isEmpty();
        assertThat(policy.allowedActions(state(AgentStage.CANDIDATES_RETRIEVED)))
                .containsExactly(AgentAction.DEDUPLICATE_CANDIDATES);
        assertThat(policy.allowedActions(state(AgentStage.CANDIDATES_DEDUPLICATED)))
                .containsExactly(AgentAction.EVALUATE_RESULTS);
        assertThat(policy.allowedActions(state(AgentStage.VERIFICATION_COMPLETED)))
                .containsExactly(AgentAction.EVALUATE_RESULTS);
        assertThat(policy.allowedActions(state(AgentStage.EVALUATING_RESULTS)))
                .containsExactlyInAnyOrder(AgentAction.REFINE_PLAN, AgentAction.COMPLETE)
                .doesNotContain(AgentAction.TERMINATE);
        assertThatThrownBy(() -> policy.allowedActions(state(AgentStage.PLAN_READY)).add(AgentAction.COMPLETE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReserveTerminationForJavaAndRejectTerminalStates() {
        AgentState active = state(AgentStage.PLAN_READY);
        AgentState terminated = active.terminate(TerminationReason.DEADLINE_EXCEEDED, "deadline", Instant.now(CLOCK));

        assertThat(policy.canExecute(active, AgentAction.TERMINATE)).isTrue();
        assertThat(policy.isAllowed(active, AgentAction.TERMINATE)).isFalse();
        assertThat(policy.allowedActions(terminated)).isEmpty();
        assertThat(policy.canExecute(terminated, AgentAction.TERMINATE)).isFalse();
    }

    @Test
    void shouldChooseVerificationOnlyWhenDeduplicatedCandidatesExist() {
        AgentState initial = AgentState.initialize("query", 2, CLOCK, Duration.ofSeconds(90));
        CandidatePaper candidate = new CandidatePaper("W1", "10.1000/example", "Example", List.of(), "venue",
                LocalDate.of(2026, 1, 1), 2026, "article", "en", 0, null, null, null, false,
                CandidatePaper.CandidateSource.OPENALEX);
        NormalizedCandidate normalized = new NormalizedCandidate("W1", candidate, "10.1000/example", "W1",
                "example", null, 2026, "venue", 0);
        AgentState state = new AgentState("query", 2, plan(), List.of(plan()), AgentStage.CANDIDATES_DEDUPLICATED,
                null, List.of(candidate), List.of(normalized), List.of(), List.of(), 0, 0, 0, 1, 0,
                Set.of(), 1, List.of(), initial.startedAt(), initial.deadline(), null, null, null);

        assertThat(policy.allowedActions(state)).containsExactly(AgentAction.VERIFY_WITH_CROSSREF);
    }

    private AgentState state(AgentStage stage) {
        AgentState initial = AgentState.initialize("query", 2, CLOCK, Duration.ofSeconds(90));
        return new AgentState("query", 2, plan(), List.of(plan()), stage, null, List.of(), List.of(), List.of(), List.of(),
                0, 0, 0, 0, 0, Set.of(), 0, List.of(), initial.startedAt(), initial.deadline(), null, null, null);
    }

    private SearchPlan plan() {
        return new SearchPlan("query", "topic", List.of("keyword"), "keyword", Set.of(LanguageCode.EN),
                List.of("article"), SearchSort.RELEVANCE, 2020, 2026, 2, 2);
    }
}
