package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
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

class AgentStateTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-28T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(STARTED_AT, ZoneOffset.UTC);

    @Test
    void shouldInitializeControlledStateFromInjectedClock() {
        AgentState state = AgentState.initialize("remote sensing change detection", 10, CLOCK, Duration.ofSeconds(90));

        assertThat(state.currentStage()).isEqualTo(AgentStage.INITIALIZED);
        assertThat(state.searchRoundCount()).isZero();
        assertThat(state.planAdjustmentCount()).isZero();
        assertThat(state.businessStepCount()).isZero();
        assertThat(state.uniqueCandidateCount()).isZero();
        assertThat(state.crossrefCallCount()).isZero();
        assertThat(state.terminationReason()).isNull();
        assertThat(state.terminated()).isFalse();
        assertThat(state.startedAt()).isEqualTo(STARTED_AT);
        assertThat(state.deadline()).isEqualTo(STARTED_AT.plusSeconds(90));
    }

    @Test
    void shouldRegisterPlanAndPreserveOriginalQuery() {
        AgentState state = initialized().recordInitialPlan(plan());

        assertThat(state.currentPlan()).isEqualTo(plan());
        assertThat(state.planHistory()).containsExactly(plan());
        assertThat(state.currentStage()).isEqualTo(AgentStage.PLAN_READY);
        assertThat(state.originalQuery()).isEqualTo("remote sensing change detection");
    }

    @Test
    void shouldAccumulateUniqueCandidatesAcrossRoundsWithoutCountingStableDuplicates() {
        AgentState firstRound = initialized().recordInitialPlan(plan())
                .startAction(new ActionExecutionPermit(AgentAction.DEDUPLICATE_CANDIDATES, new ActionCost(2, 0)))
                .recordDeduplicatedCandidates(deduplication(normalized("W1", "10.1000/a")),
                        new ActionExecutionPermit(AgentAction.DEDUPLICATE_CANDIDATES, new ActionCost(2, 0)));

        AgentState secondRound = firstRound
                .startAction(new ActionExecutionPermit(AgentAction.DEDUPLICATE_CANDIDATES, new ActionCost(2, 0)))
                .recordDeduplicatedCandidates(deduplication(normalized("W1-again", "https://doi.org/10.1000/A"),
                        normalized("W2", "10.1000/b")),
                        new ActionExecutionPermit(AgentAction.DEDUPLICATE_CANDIDATES, new ActionCost(2, 0)));

        assertThat(secondRound.uniqueCandidateCount()).isEqualTo(2);
        assertThat(secondRound.globalCandidateKeys()).hasSize(2);
        assertThat(secondRound.businessStepCount()).isEqualTo(2);
    }

    @Test
    void shouldAccumulateSearchRoundsCrossrefCallsAndObservations() {
        AgentState state = initialized().recordInitialPlan(plan())
                .startAction(new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()))
                .recordSearchResult(List.of(candidate("W1", "10.1000/a")))
                .recordObservation(observation(AgentAction.SEARCH_OPENALEX, 0),
                        new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()))
                .startAction(new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()))
                .recordSearchResult(List.of(candidate("W2", "10.1000/b")))
                .recordObservation(observation(AgentAction.SEARCH_OPENALEX, 0),
                        new ActionExecutionPermit(AgentAction.SEARCH_OPENALEX, ActionCost.none()))
                .startAction(new ActionExecutionPermit(AgentAction.VERIFY_WITH_CROSSREF, new ActionCost(0, 18)))
                .recordObservation(observation(AgentAction.VERIFY_WITH_CROSSREF, 18),
                        new ActionExecutionPermit(AgentAction.VERIFY_WITH_CROSSREF, new ActionCost(0, 18)));

        assertThat(state.searchRoundCount()).isEqualTo(2);
        assertThat(state.crossrefCallCount()).isEqualTo(18);
        assertThat(state.observations()).hasSize(3);
    }

    private AgentState initialized() {
        return AgentState.initialize("remote sensing change detection", 10, CLOCK, Duration.ofSeconds(90));
    }

    private SearchPlan plan() {
        return new SearchPlan("remote sensing change detection", "topic", List.of("remote", "sensing"), "remote sensing",
                Set.of(LanguageCode.EN), List.of("article"), SearchSort.RELEVANCE, 2020, 2026, 20, 10);
    }

    private CandidateDeduplicationResult deduplication(NormalizedCandidate... candidates) {
        return new CandidateDeduplicationResult(List.of(candidates), List.of(), candidates.length, candidates.length, 0);
    }

    private NormalizedCandidate normalized(String id, String doi) {
        return new NormalizedCandidate(id, candidate(id, doi), doi.replace("https://doi.org/", "").toLowerCase(), id,
                "paper " + id, "author", 2026, "journal", 0);
    }

    private CandidatePaper candidate(String id, String doi) {
        return new CandidatePaper(id, doi, "Paper " + id, List.of(), "Journal", LocalDate.of(2026, 1, 1), 2026,
                "article", "en", 1, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
    }

    private AgentObservation observation(AgentAction action, int crossrefCalls) {
        return new AgentObservation(action, AgentStage.SEARCHING, AgentStage.CANDIDATES_RETRIEVED, true,
                1, 0, 0, 0, crossrefCalls, Duration.ZERO, "test observation", null, STARTED_AT);
    }

}
