package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.literature.application.CandidateDeduplicationService;
import com.dj1012h.researchpilot.literature.application.CrossrefCandidateLookupService;
import com.dj1012h.researchpilot.literature.application.CrossrefLookupSummary;
import com.dj1012h.researchpilot.literature.application.EligiblePaperFilter;
import com.dj1012h.researchpilot.literature.application.OpenAlexQueryFactory;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationKey;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.SearchConstraintField;
import com.dj1012h.researchpilot.literature.model.SearchConstraintOrigins;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchActionExecutorTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OpenAlexSearchPort openAlex = mock(OpenAlexSearchPort.class);
    private final CandidateDeduplicationService deduplication = mock(CandidateDeduplicationService.class);
    private final CrossrefCandidateLookupService crossref = mock(CrossrefCandidateLookupService.class);
    private final PaperVerificationService verification = mock(PaperVerificationService.class);
    private final EligiblePaperFilter eligible = mock(EligiblePaperFilter.class);
    private final SearchPlanRefiner refiner = mock(SearchPlanRefiner.class);
    private AgentBudgetProperties budgets;
    private SearchActionExecutor executor;

    @BeforeEach
    void setUp() {
        budgets = new AgentBudgetProperties();
        executor = new SearchActionExecutor(
                new AgentTransitionPolicy(),
                new AgentBudgetPolicy(budgets, CLOCK),
                budgets,
                new OpenAlexQueryFactory(),
                openAlex,
                deduplication,
                crossref,
                verification,
                eligible,
                refiner,
                CLOCK
        );
    }

    @Test
    void shouldBlockInvalidActionBeforeAnyExternalTool() {
        AgentExecutionContext context = context(planReady());

        SearchActionExecutionResult result = executor.execute(
                context, AgentAction.VERIFY_WITH_CROSSREF, ActionDecisionSource.POLICY_SINGLE_ACTION);

        assertThat(result.status()).isEqualTo(ExecutionStepStatus.BLOCKED);
        assertThat(result.context().state().terminationReason()).isEqualTo(TerminationReason.INVALID_STATE);
        verify(openAlex, never()).search(any());
        verify(crossref, never()).lookup(any(CandidateDeduplicationResult.class));
    }

    @Test
    void shouldTerminateSafelyWhenOpenAlexIsUnavailable() {
        when(openAlex.search(any())).thenThrow(new IllegalStateException("raw provider body"));

        SearchActionExecutionResult result = executor.execute(
                context(planReady()), AgentAction.SEARCH_OPENALEX, ActionDecisionSource.POLICY_SINGLE_ACTION);

        assertThat(result.status()).isEqualTo(ExecutionStepStatus.FAILED);
        assertThat(result.context().state().terminationReason())
                .isEqualTo(TerminationReason.EXTERNAL_SERVICE_UNAVAILABLE);
        assertThat(result.observationSummary()).doesNotContain("raw provider body");
        verify(crossref, never()).lookup(any(CandidateDeduplicationResult.class));
    }

    @Test
    void shouldRemoveCrossRoundDuplicatesBeforeCrossref() {
        NormalizedCandidate duplicate = normalized("W1-again", "10.1000/a");
        NormalizedCandidate fresh = normalized("W2", "10.1000/b");
        CandidateDeduplicationResult round = new CandidateDeduplicationResult(
                List.of(duplicate, fresh), List.of(), 2, 2, 0);
        when(deduplication.deduplicate(any())).thenReturn(round);
        AgentState state = stateAt(
                AgentStage.CANDIDATES_RETRIEVED,
                List.of(duplicate.originalCandidate(), fresh.originalCandidate()),
                List.of(),
                1,
                Set.of(CandidateDeduplicationKey.from(normalized("W1", "10.1000/a")).orElseThrow()),
                0,
                0
        );

        SearchActionExecutionResult result = executor.execute(
                context(state), AgentAction.DEDUPLICATE_CANDIDATES, ActionDecisionSource.POLICY_SINGLE_ACTION);

        assertThat(result.status()).isEqualTo(ExecutionStepStatus.SUCCEEDED);
        assertThat(result.context().state().deduplicatedCandidates()).containsExactly(fresh);
        assertThat(result.context().state().uniqueCandidateCount()).isEqualTo(2);
        assertThat(result.context().currentRoundDeduplication().uniqueCandidates()).containsExactly(fresh);
    }

    @Test
    void shouldPreserveVerifiedResultsAndTerminateWhenCrossrefBecomesUnavailable() {
        NormalizedCandidate candidate = normalized("W2", "10.1000/b");
        CandidateDeduplicationResult current = new CandidateDeduplicationResult(
                List.of(candidate), List.of(), 1, 1, 0);
        CrossrefLookupSummary summary = mock(CrossrefLookupSummary.class);
        when(summary.crossrefEnabled()).thenReturn(true);
        when(summary.sourceAvailable()).thenReturn(false);
        when(summary.attemptedCount()).thenReturn(1);
        when(crossref.lookup(current)).thenReturn(summary);
        when(verification.verify(summary)).thenReturn(List.of());
        when(eligible.filter(any(), any(Integer.class))).thenReturn(List.of());
        AgentState state = stateAt(
                AgentStage.CANDIDATES_DEDUPLICATED,
                List.of(candidate.originalCandidate()),
                List.of(candidate),
                1,
                Set.of(CandidateDeduplicationKey.from(candidate).orElseThrow()),
                0,
                0
        );
        AgentExecutionContext context = new AgentExecutionContext(
                state, validated(), null, current);

        SearchActionExecutionResult result = executor.execute(
                context, AgentAction.VERIFY_WITH_CROSSREF, ActionDecisionSource.POLICY_SINGLE_ACTION);

        assertThat(result.status()).isEqualTo(ExecutionStepStatus.FAILED);
        assertThat(result.context().state().terminationReason())
                .isEqualTo(TerminationReason.EXTERNAL_SERVICE_UNAVAILABLE);
        assertThat(result.context().state().crossrefCallCount()).isOne();
        assertThat(result.failureCode()).isEqualTo("CROSSREF_SOURCE_UNAVAILABLE");
    }

    @Test
    void shouldBlockCandidateBudgetBeforeOpenAlex() {
        budgets.setMaxUniqueCandidates(1);
        AgentState state = stateAt(
                AgentStage.PLAN_READY, List.of(), List.of(), 1,
                Set.of(CandidateDeduplicationKey.from(normalized("W1", "10.1000/a")).orElseThrow()),
                0, 0);

        SearchActionExecutionResult result = executor.execute(
                context(state), AgentAction.SEARCH_OPENALEX, ActionDecisionSource.POLICY_SINGLE_ACTION);

        assertThat(result.status()).isEqualTo(ExecutionStepStatus.BLOCKED);
        assertThat(result.context().state().terminationReason())
                .isEqualTo(TerminationReason.CANDIDATE_BUDGET_EXHAUSTED);
        verify(openAlex, never()).search(any());
    }

    @Test
    void shouldBlockSearchRefinementStepAndCrossrefBudgetsBeforeTools() {
        NormalizedCandidate candidate = normalized("W1", "10.1000/a");
        Set<CandidateDeduplicationKey> keys =
                Set.of(CandidateDeduplicationKey.from(candidate).orElseThrow());

        SearchActionExecutionResult searchBlocked = executor.execute(
                context(stateAt(AgentStage.PLAN_READY, List.of(), List.of(), 0, Set.of(), 0, 0, 2, 0)),
                AgentAction.SEARCH_OPENALEX,
                ActionDecisionSource.POLICY_SINGLE_ACTION);
        SearchActionExecutionResult refinementBlocked = executor.execute(
                context(stateAt(AgentStage.EVALUATING_RESULTS, List.of(), List.of(), 0, Set.of(), 0, 1, 1, 0)),
                AgentAction.REFINE_PLAN,
                ActionDecisionSource.MODEL);
        SearchActionExecutionResult stepBlocked = executor.execute(
                context(stateAt(AgentStage.PLAN_READY, List.of(), List.of(), 0, Set.of(), 0, 0, 0, 10)),
                AgentAction.SEARCH_OPENALEX,
                ActionDecisionSource.POLICY_SINGLE_ACTION);
        CandidateDeduplicationResult current = new CandidateDeduplicationResult(
                List.of(candidate), List.of(), 1, 1, 0);
        AgentState crossrefState = stateAt(
                AgentStage.CANDIDATES_DEDUPLICATED, List.of(candidate.originalCandidate()),
                List.of(candidate), 1, keys, 45, 0, 0, 0);
        SearchActionExecutionResult crossrefBlocked = executor.execute(
                new AgentExecutionContext(crossrefState, validated(), null, current),
                AgentAction.VERIFY_WITH_CROSSREF,
                ActionDecisionSource.POLICY_SINGLE_ACTION);

        assertThat(searchBlocked.context().state().terminationReason())
                .isEqualTo(TerminationReason.SEARCH_ROUND_LIMIT_REACHED);
        assertThat(refinementBlocked.context().state().terminationReason())
                .isEqualTo(TerminationReason.PLAN_ADJUSTMENT_LIMIT_REACHED);
        assertThat(stepBlocked.context().state().terminationReason())
                .isEqualTo(TerminationReason.STEP_LIMIT_REACHED);
        assertThat(crossrefBlocked.context().state().terminationReason())
                .isEqualTo(TerminationReason.CROSSREF_BUDGET_EXHAUSTED);
        assertThat(List.of(searchBlocked, refinementBlocked, stepBlocked, crossrefBlocked))
                .extracting(SearchActionExecutionResult::status)
                .containsOnly(ExecutionStepStatus.BLOCKED);
        verify(openAlex, never()).search(any());
        verify(crossref, never()).lookup(any(CandidateDeduplicationResult.class));
        verify(refiner, never()).refine(any());
    }

    private AgentExecutionContext context(AgentState state) {
        return AgentExecutionContext.initial(state, validated());
    }

    private AgentState planReady() {
        return AgentState.initialize("query", 2, CLOCK, Duration.ofSeconds(90)).recordInitialPlan(plan());
    }

    private AgentState stateAt(
            AgentStage stage,
            List<CandidatePaper> retrieved,
            List<NormalizedCandidate> deduplicatedCandidates,
            int uniqueCount,
            Set<CandidateDeduplicationKey> keys,
            int crossrefCalls,
            int planAdjustments
    ) {
        return stateAt(stage, retrieved, deduplicatedCandidates, uniqueCount, keys,
                crossrefCalls, planAdjustments, 0, 0);
    }

    private AgentState stateAt(
            AgentStage stage,
            List<CandidatePaper> retrieved,
            List<NormalizedCandidate> deduplicatedCandidates,
            int uniqueCount,
            Set<CandidateDeduplicationKey> keys,
            int crossrefCalls,
            int planAdjustments,
            int searchRounds,
            int businessSteps
    ) {
        AgentState initial = AgentState.initialize("query", 2, CLOCK, Duration.ofSeconds(90));
        return new AgentState(
                "query", 2, plan(), List.of(plan()), stage, null, retrieved, deduplicatedCandidates,
                List.of(), List.of(), searchRounds, planAdjustments, businessSteps, uniqueCount, crossrefCalls,
                keys, uniqueCount - keys.size(), List.of(), initial.startedAt(), initial.deadline(),
                null, null, null
        );
    }

    private ValidatedSearchPlanContext validated() {
        SearchRequest request = new SearchRequest("query", 2020, 2026, 2);
        SearchPlanGenerationContext generation = new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000031"), request, NOW, 2026);
        Map<SearchConstraintField, ConstraintOrigin> origins = Arrays.stream(SearchConstraintField.values())
                .collect(Collectors.toMap(field -> field, field -> ConstraintOrigin.SYSTEM_FIXED));
        return new ValidatedSearchPlanContext(
                generation, new SearchPlanValidationResult(plan(), new SearchConstraintOrigins(origins)));
    }

    private SearchPlan plan() {
        return new SearchPlan("query", "topic", List.of("keyword"), "keyword", Set.of(LanguageCode.EN),
                List.of("article"), SearchSort.RELEVANCE, 2020, 2026, 2, 2);
    }

    private NormalizedCandidate normalized(String id, String doi) {
        CandidatePaper candidate = new CandidatePaper(
                id, doi, "Paper " + id, List.of(), "Journal", LocalDate.of(2026, 1, 1), 2026,
                "article", "en", 1, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
        return new NormalizedCandidate(
                id, candidate, doi.toLowerCase(), id, "paper " + id, null, 2026, "journal", 0);
    }
}
