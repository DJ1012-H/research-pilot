package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.CandidateDeduplicationService;
import com.dj1012h.researchpilot.literature.application.CrossrefCandidateLookupService;
import com.dj1012h.researchpilot.literature.application.CrossrefLookupSummary;
import com.dj1012h.researchpilot.literature.application.EligiblePaperFilter;
import com.dj1012h.researchpilot.literature.application.OpenAlexQueryFactory;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchConstraintField;
import com.dj1012h.researchpilot.literature.model.SearchConstraintOrigins;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionLoopTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OpenAlexSearchPort openAlex = mock(OpenAlexSearchPort.class);
    private final CandidateDeduplicationService deduplication = mock(CandidateDeduplicationService.class);
    private final CrossrefCandidateLookupService crossref = mock(CrossrefCandidateLookupService.class);
    private final PaperVerificationService verification = mock(PaperVerificationService.class);
    private final EligiblePaperFilter eligible = mock(EligiblePaperFilter.class);
    private final SearchPlanRefiner refiner = mock(SearchPlanRefiner.class);
    private final SearchActionDecider decider = mock(SearchActionDecider.class);
    private AgentBudgetProperties budgets;
    private LiteratureResearchAgent agent;

    @BeforeEach
    void setUp() {
        budgets = new AgentBudgetProperties();
        AgentBudgetPolicy budgetPolicy = new AgentBudgetPolicy(budgets, CLOCK);
        AgentTransitionPolicy transitions = new AgentTransitionPolicy();
        SearchActionExecutor executor = new SearchActionExecutor(
                transitions, budgetPolicy, budgets, new OpenAlexQueryFactory(), openAlex,
                deduplication, crossref, verification, eligible, refiner, CLOCK);
        agent = new LiteratureResearchAgent(
                budgetPolicy, budgets, new LiteratureSearchProperties(), openAlex,
                transitions, decider, executor, new InMemoryExecutionTraceRecorder(), CLOCK);
    }

    @Test
    void shouldCompleteDeterministicFirstRoundWithoutCallingDecisionModel() {
        CandidatePaper first = candidate("W1", "10.1000/a");
        CandidatePaper second = candidate("W2", "10.1000/b");
        CandidateDeduplicationResult deduplicated = deduplication(first, second);
        CrossrefLookupSummary summary = availableSummary(2);
        List<CandidateVerificationOutcome> outcomes = List.of(outcome(first), outcome(second));
        List<SearchResponse.PaperResult> papers = List.of(paper(first), paper(second));
        when(openAlex.search(any())).thenReturn(new OpenAlexSearchResult(2, List.of(first, second), null));
        when(deduplication.deduplicate(List.of(first, second))).thenReturn(deduplicated);
        when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt())).thenReturn(summary);
        when(verification.verify(summary)).thenReturn(outcomes);
        when(eligible.filter(outcomes, 2)).thenReturn(papers);

        AgentRunResult run = agent.execute(initialized(2), validated(plan(2)));

        assertThat(run.context().state().terminationReason()).isEqualTo(TerminationReason.TARGET_REACHED);
        assertThat(run.context().state().verifiedPapers()).containsExactlyElementsOf(papers);
        assertThat(run.context().state().searchRoundCount()).isOne();
        assertThat(run.context().state().planAdjustmentCount()).isZero();
        assertThat(run.trace()).extracting(ExecutionTraceEntry::action).containsExactly(
                AgentAction.SEARCH_OPENALEX,
                AgentAction.DEDUPLICATE_CANDIDATES,
                AgentAction.VERIFY_WITH_CROSSREF,
                AgentAction.EVALUATE_RESULTS,
                AgentAction.COMPLETE
        );
        verify(decider, never()).decide(any());
        verify(refiner, never()).refine(any());
        verify(openAlex, times(1)).search(any());
    }

    @Test
    void shouldRefineOnceUseTheTrustedRefinedContextAndReachTargetOnSecondRound() {
        CandidatePaper first = candidate("W1", "10.1000/a");
        CandidatePaper duplicate = candidate("W1-again", "10.1000/a");
        CandidatePaper second = candidate("W2", "10.1000/b");
        CandidatePaper third = candidate("W3", "10.1000/c");
        CrossrefLookupSummary firstSummary = availableSummary(1);
        CrossrefLookupSummary secondSummary = availableSummary(2);
        List<CandidateVerificationOutcome> firstOutcomes = List.of(outcome(first));
        List<CandidateVerificationOutcome> secondOutcomes = List.of(outcome(second), outcome(third));
        List<SearchResponse.PaperResult> firstPapers = List.of(paper(first));
        List<SearchResponse.PaperResult> finalPapers = List.of(paper(first), paper(second), paper(third));
        when(openAlex.search(any()))
                .thenReturn(new OpenAlexSearchResult(1, List.of(first), null))
                .thenReturn(new OpenAlexSearchResult(3, List.of(duplicate, second, third), null));
        when(deduplication.deduplicate(List.of(first))).thenReturn(deduplication(first));
        when(deduplication.deduplicate(List.of(duplicate, second, third)))
                .thenReturn(deduplication(duplicate, second, third));
        when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt()))
                .thenReturn(firstSummary)
                .thenReturn(secondSummary);
        when(verification.verify(firstSummary)).thenReturn(firstOutcomes);
        when(verification.verify(secondSummary)).thenReturn(secondOutcomes);
        when(eligible.filter(firstOutcomes, 3)).thenReturn(firstPapers);
        when(eligible.filter(any(), anyInt())).thenAnswer(invocation -> {
            List<?> values = invocation.getArgument(0);
            return values.size() == 3 ? finalPapers : firstPapers;
        });
        when(decider.decide(any())).thenReturn(
                new SearchActionDecision(AgentAction.REFINE_PLAN, ActionDecisionSource.MODEL));
        SearchPlan initialPlan = plan(3);
        SearchPlan refinedPlan = new SearchPlan(
                initialPlan.originalQuery(), initialPlan.topic(), List.of("keyword", "synonym"),
                "keyword OR synonym", initialPlan.languages(), initialPlan.publicationTypes(), initialPlan.sort(),
                initialPlan.fromYear(), initialPlan.toYear(), initialPlan.candidateLimit(), initialPlan.resultLimit());
        when(refiner.refine(any())).thenReturn(new SearchPlanRefinementResult(
                refinedPlan, origins(), new SearchPlanDiff(
                List.of("synonym"), List.of(), List.of("ORIGINAL_QUERY"), "broaden query"), 1));

        AgentRunResult run = agent.execute(initialized(3), validated(initialPlan));

        AgentState state = run.context().state();
        assertThat(state.terminationReason()).isEqualTo(TerminationReason.TARGET_REACHED);
        assertThat(state.searchRoundCount()).isEqualTo(2);
        assertThat(state.planAdjustmentCount()).isOne();
        assertThat(state.planHistory()).containsExactly(initialPlan, refinedPlan);
        assertThat(run.context().validatedPlanContext().validationResult().plan()).isEqualTo(refinedPlan);
        assertThat(state.currentPlan()).isEqualTo(refinedPlan);
        assertThat(state.verifiedPapers()).hasSize(3);
        assertThat(run.trace()).extracting(ExecutionTraceEntry::action).containsExactly(
                AgentAction.SEARCH_OPENALEX,
                AgentAction.DEDUPLICATE_CANDIDATES,
                AgentAction.VERIFY_WITH_CROSSREF,
                AgentAction.EVALUATE_RESULTS,
                AgentAction.REFINE_PLAN,
                AgentAction.SEARCH_OPENALEX,
                AgentAction.DEDUPLICATE_CANDIDATES,
                AgentAction.VERIFY_WITH_CROSSREF,
                AgentAction.EVALUATE_RESULTS,
                AgentAction.COMPLETE
        );
        verify(refiner, times(1)).refine(any());
        verify(decider, times(1)).decide(any());
        ArgumentCaptor<CandidateDeduplicationResult> capture =
                ArgumentCaptor.forClass(CandidateDeduplicationResult.class);
        verify(crossref, times(2)).lookup(capture.capture(), anyInt());
        assertThat(capture.getAllValues().get(1).uniqueOriginalCandidates()).containsExactly(second, third);
    }

    @Test
    void shouldReturnPartialResultsAfterTheOnlyRefinement() {
        CandidatePaper first = candidate("W1", "10.1000/a");
        CandidatePaper second = candidate("W2", "10.1000/b");
        stubTwoRounds(first, second, 5);
        when(decider.decide(any()))
                .thenReturn(new SearchActionDecision(AgentAction.REFINE_PLAN, ActionDecisionSource.MODEL))
                .thenReturn(new SearchActionDecision(AgentAction.COMPLETE, ActionDecisionSource.POLICY_SINGLE_ACTION));
        SearchPlan initialPlan = plan(5);
        SearchPlan refinedPlan = new SearchPlan(
                initialPlan.originalQuery(), initialPlan.topic(), List.of("keyword", "synonym"),
                "keyword OR synonym", initialPlan.languages(), initialPlan.publicationTypes(), initialPlan.sort(),
                initialPlan.fromYear(), initialPlan.toYear(), initialPlan.candidateLimit(), initialPlan.resultLimit());
        when(refiner.refine(any())).thenReturn(new SearchPlanRefinementResult(
                refinedPlan, origins(), new SearchPlanDiff(
                List.of("synonym"), List.of(), List.of(), "broaden query"), 1));

        AgentRunResult run = agent.execute(initialized(5), validated(initialPlan));

        assertThat(run.context().state().terminationReason()).isEqualTo(TerminationReason.PARTIAL_RESULTS);
        assertThat(run.context().state().verifiedPapers()).hasSize(2);
        assertThat(run.context().state().searchRoundCount()).isEqualTo(2);
        assertThat(run.context().state().planAdjustmentCount()).isOne();
        verify(openAlex, times(2)).search(any());
        verify(refiner, times(1)).refine(any());
    }

    @Test
    void shouldReturnNoVerifiedResultsAfterTheOnlyRefinement() {
        CandidatePaper first = candidate("W1", "10.1000/a");
        CandidatePaper second = candidate("W2", "10.1000/b");
        CrossrefLookupSummary firstSummary = availableSummary(1);
        CrossrefLookupSummary secondSummary = availableSummary(1);
        SearchPlan initialPlan = plan(2);
        SearchPlan refinedPlan = new SearchPlan(
                initialPlan.originalQuery(), initialPlan.topic(), List.of("keyword", "synonym"),
                "keyword OR synonym", initialPlan.languages(), initialPlan.publicationTypes(), initialPlan.sort(),
                initialPlan.fromYear(), initialPlan.toYear(), initialPlan.candidateLimit(), initialPlan.resultLimit());
        when(openAlex.search(any()))
                .thenReturn(new OpenAlexSearchResult(1, List.of(first), null))
                .thenReturn(new OpenAlexSearchResult(1, List.of(second), null));
        when(deduplication.deduplicate(List.of(first))).thenReturn(deduplication(first));
        when(deduplication.deduplicate(List.of(second))).thenReturn(deduplication(second));
        when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt()))
                .thenReturn(firstSummary)
                .thenReturn(secondSummary);
        when(verification.verify(firstSummary)).thenReturn(List.of());
        when(verification.verify(secondSummary)).thenReturn(List.of());
        when(eligible.filter(any(), anyInt())).thenReturn(List.of());
        when(decider.decide(any()))
                .thenReturn(new SearchActionDecision(AgentAction.REFINE_PLAN, ActionDecisionSource.MODEL))
                .thenReturn(new SearchActionDecision(AgentAction.COMPLETE, ActionDecisionSource.POLICY_SINGLE_ACTION));
        when(refiner.refine(any())).thenReturn(new SearchPlanRefinementResult(
                refinedPlan, origins(), new SearchPlanDiff(
                List.of("synonym"), List.of(), List.of("ORIGINAL_QUERY"), "broaden query"), 1));

        AgentRunResult run = agent.execute(initialized(2), validated(initialPlan));

        assertThat(run.context().state().terminationReason()).isEqualTo(TerminationReason.NO_VERIFIED_RESULTS);
        assertThat(run.context().state().verifiedPapers()).isEmpty();
        assertThat(run.context().state().searchRoundCount()).isEqualTo(2);
        assertThat(run.context().state().planAdjustmentCount()).isOne();
        assertThat(run.trace()).extracting(ExecutionTraceEntry::action).containsExactly(
                AgentAction.SEARCH_OPENALEX,
                AgentAction.DEDUPLICATE_CANDIDATES,
                AgentAction.VERIFY_WITH_CROSSREF,
                AgentAction.EVALUATE_RESULTS,
                AgentAction.REFINE_PLAN,
                AgentAction.SEARCH_OPENALEX,
                AgentAction.DEDUPLICATE_CANDIDATES,
                AgentAction.VERIFY_WITH_CROSSREF,
                AgentAction.EVALUATE_RESULTS,
                AgentAction.COMPLETE
        );
        verify(openAlex, times(2)).search(any());
        verify(crossref, times(2)).lookup(any(CandidateDeduplicationResult.class), anyInt());
        verify(refiner, times(1)).refine(any());
    }

    @Test
    void shouldCompleteWithNoVerifiedResultsWhenDecisionDeclinesRefinement() {
        CandidatePaper candidate = candidate("W1", "10.1000/a");
        CrossrefLookupSummary summary = availableSummary(1);
        when(openAlex.search(any())).thenReturn(new OpenAlexSearchResult(1, List.of(candidate), null));
        when(deduplication.deduplicate(List.of(candidate))).thenReturn(deduplication(candidate));
        when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt())).thenReturn(summary);
        when(verification.verify(summary)).thenReturn(List.of());
        when(eligible.filter(any(), anyInt())).thenReturn(List.of());
        when(decider.decide(any())).thenReturn(
                new SearchActionDecision(AgentAction.COMPLETE, ActionDecisionSource.MODEL));

        AgentRunResult run = agent.execute(initialized(2), validated(plan(2)));

        assertThat(run.context().state().terminationReason())
                .isEqualTo(TerminationReason.NO_VERIFIED_RESULTS);
        assertThat(run.context().state().verifiedPapers()).isEmpty();
        assertThat(run.context().state().searchRoundCount()).isOne();
        verify(refiner, never()).refine(any());
        verify(openAlex, times(1)).search(any());
    }

    @Test
    void shouldStopAtDeadlineBeforeCallingModelOrExternalToolsAndTraceTheReason() {
        Clock expiredClock = Clock.fixed(NOW.plusSeconds(91), ZoneOffset.UTC);
        AgentBudgetPolicy expiredPolicy = new AgentBudgetPolicy(budgets, expiredClock);
        SearchActionExecutor expiredExecutor = new SearchActionExecutor(
                new AgentTransitionPolicy(), expiredPolicy, budgets, new OpenAlexQueryFactory(), openAlex,
                deduplication, crossref, verification, eligible, refiner, expiredClock);
        LiteratureResearchAgent expiredAgent = new LiteratureResearchAgent(
                expiredPolicy, budgets, new LiteratureSearchProperties(), openAlex,
                new AgentTransitionPolicy(), decider, expiredExecutor,
                new InMemoryExecutionTraceRecorder(), expiredClock);

        AgentRunResult run = expiredAgent.execute(initialized(2), validated(plan(2)));

        assertThat(run.context().state().terminationReason()).isEqualTo(TerminationReason.DEADLINE_EXCEEDED);
        assertThat(run.trace()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AgentAction.TERMINATE);
            assertThat(entry.status()).isEqualTo(ExecutionStepStatus.BLOCKED);
            assertThat(entry.terminationReason()).isEqualTo(TerminationReason.DEADLINE_EXCEEDED);
        });
        verify(openAlex, never()).search(any());
        verify(decider, never()).decide(any());
    }

    @Test
    void shouldTerminateInvalidPlanContextBeforeAnyToolCall() {
        AgentState initialized = initialized(2);
        AgentState planReady = agent.registerInitialPlan(initialized, plan(2));
        SearchPlan inconsistent = new SearchPlan(
                "query", "different topic", List.of("other"), "other", Set.of(LanguageCode.EN),
                List.of("article"), SearchSort.RELEVANCE, 2020, 2026, 5, 2);

        AgentRunResult run = agent.execute(planReady, validated(inconsistent));

        assertThat(run.context().state().terminationReason()).isEqualTo(TerminationReason.INVALID_STATE);
        assertThat(run.trace()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AgentAction.TERMINATE);
            assertThat(entry.status()).isEqualTo(ExecutionStepStatus.BLOCKED);
        });
        verify(openAlex, never()).search(any());
        verify(crossref, never()).lookup(any(CandidateDeduplicationResult.class), anyInt());
        verify(decider, never()).decide(any());
    }

    private void stubTwoRounds(CandidatePaper first, CandidatePaper second, int requestedCount) {
        CrossrefLookupSummary firstSummary = availableSummary(1);
        CrossrefLookupSummary secondSummary = availableSummary(1);
        CandidateVerificationOutcome firstOutcome = outcome(first);
        CandidateVerificationOutcome secondOutcome = outcome(second);
        when(openAlex.search(any()))
                .thenReturn(new OpenAlexSearchResult(1, List.of(first), null))
                .thenReturn(new OpenAlexSearchResult(1, List.of(second), null));
        when(deduplication.deduplicate(List.of(first))).thenReturn(deduplication(first));
        when(deduplication.deduplicate(List.of(second))).thenReturn(deduplication(second));
        when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt()))
                .thenReturn(firstSummary)
                .thenReturn(secondSummary);
        when(verification.verify(firstSummary)).thenReturn(List.of(firstOutcome));
        when(verification.verify(secondSummary)).thenReturn(List.of(secondOutcome));
        when(eligible.filter(any(), anyInt())).thenAnswer(invocation -> {
            List<?> values = invocation.getArgument(0);
            return values.size() == 1
                    ? List.of(paper(first))
                    : List.of(paper(first), paper(second));
        });
    }

    private AgentState initialized(int requestedCount) {
        return AgentState.initialize("query", requestedCount, CLOCK, Duration.ofSeconds(90));
    }

    private ValidatedSearchPlanContext validated(SearchPlan plan) {
        SearchRequest request = new SearchRequest("query", 2020, 2026, plan.resultLimit());
        SearchPlanGenerationContext generation = new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000032"), request, NOW, 2026);
        return new ValidatedSearchPlanContext(
                generation, new SearchPlanValidationResult(plan, origins()));
    }

    private SearchConstraintOrigins origins() {
        Map<SearchConstraintField, ConstraintOrigin> values = Arrays.stream(SearchConstraintField.values())
                .collect(Collectors.toMap(field -> field, field -> ConstraintOrigin.SYSTEM_FIXED));
        return new SearchConstraintOrigins(values);
    }

    private SearchPlan plan(int requestedCount) {
        return new SearchPlan("query", "topic", List.of("keyword"), "keyword", Set.of(LanguageCode.EN),
                List.of("article"), SearchSort.RELEVANCE, 2020, 2026,
                Math.max(requestedCount, 5), requestedCount);
    }

    private CandidateDeduplicationResult deduplication(CandidatePaper... candidates) {
        List<NormalizedCandidate> normalized = java.util.stream.IntStream.range(0, candidates.length)
                .mapToObj(index -> normalized(candidates[index], index))
                .toList();
        return new CandidateDeduplicationResult(
                normalized, List.of(), normalized.size(), normalized.size(), 0);
    }

    private NormalizedCandidate normalized(CandidatePaper candidate, int index) {
        return new NormalizedCandidate(
                candidate.openAlexId(), candidate, candidate.doi().toLowerCase(), candidate.openAlexId(),
                candidate.title().toLowerCase(), null, candidate.publicationYear(), "journal", index);
    }

    private CandidatePaper candidate(String id, String doi) {
        return new CandidatePaper(
                id, doi, "Paper " + id, List.of(), "Journal", LocalDate.of(2026, 1, 1), 2026,
                "article", "en", 1, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
    }

    private CandidateVerificationOutcome outcome(CandidatePaper candidate) {
        CrossrefWorkMetadata reference = new CrossrefWorkMetadata(
                candidate.doi(), candidate.title(), List.of(), 2026, "Journal", "article", "Publisher");
        return new CandidateVerificationOutcome(candidate, reference, verificationResult(candidate.doi()));
    }

    private SearchResponse.PaperResult paper(CandidatePaper candidate) {
        PaperDTO dto = new PaperDTO(
                candidate.openAlexId(), candidate.doi(), candidate.title(), List.of(), 2026, "Journal",
                List.of(), "article", null, null, "en", List.of(), 1, PaperDTO.LiteratureSource.OPENALEX);
        return new SearchResponse.PaperResult(dto, 1.0, verificationResult(candidate.doi()));
    }

    private VerificationResult verificationResult(String doi) {
        return new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED,
                1.0,
                VerificationResult.VerificationSource.CROSSREF,
                doi,
                List.of(),
                List.of("TEST")
        );
    }

    private CrossrefLookupSummary availableSummary(int attempted) {
        CrossrefLookupSummary summary = mock(CrossrefLookupSummary.class);
        when(summary.crossrefEnabled()).thenReturn(true);
        when(summary.sourceAvailable()).thenReturn(true);
        when(summary.attemptedCount()).thenReturn(attempted);
        return summary;
    }
}
