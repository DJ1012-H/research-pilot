package com.dj1012h.researchpilot.literature.demo;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.config.ReviewProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.agent.ActionDecisionSource;
import com.dj1012h.researchpilot.literature.agent.AgentAction;
import com.dj1012h.researchpilot.literature.agent.AgentBudgetPolicy;
import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.agent.AgentTransitionPolicy;
import com.dj1012h.researchpilot.literature.agent.InMemoryExecutionTraceRecorder;
import com.dj1012h.researchpilot.literature.agent.LiteratureResearchAgent;
import com.dj1012h.researchpilot.literature.agent.SearchActionDecider;
import com.dj1012h.researchpilot.literature.agent.SearchActionDecision;
import com.dj1012h.researchpilot.literature.agent.SearchActionExecutor;
import com.dj1012h.researchpilot.literature.agent.SearchPlanDiff;
import com.dj1012h.researchpilot.literature.agent.SearchPlanRefinementResult;
import com.dj1012h.researchpilot.literature.agent.SearchPlanRefiner;
import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.CandidateDeduplicationService;
import com.dj1012h.researchpilot.literature.application.CrossrefCandidateLookupService;
import com.dj1012h.researchpilot.literature.application.CrossrefLookupSummary;
import com.dj1012h.researchpilot.literature.application.EligiblePaperFilter;
import com.dj1012h.researchpilot.literature.application.OpenAlexQueryFactory;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import com.dj1012h.researchpilot.literature.application.ReviewResponseAssembler;
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
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.review.CitationGuard;
import com.dj1012h.researchpilot.literature.review.CitationIdParser;
import com.dj1012h.researchpilot.literature.review.EvidenceReviewGenerator;
import com.dj1012h.researchpilot.literature.review.EvidenceReviewOrchestrator;
import com.dj1012h.researchpilot.literature.review.EvidenceReviewPromptBuilder;
import com.dj1012h.researchpilot.literature.review.EvidenceReviewRepairPromptBuilder;
import com.dj1012h.researchpilot.literature.review.ReviewDraftBusinessValidator;
import com.dj1012h.researchpilot.literature.review.ReviewDraftMapper;
import com.dj1012h.researchpilot.literature.review.ReviewDraftSchemaValidator;
import com.dj1012h.researchpilot.literature.review.ReviewDraftValidationPipeline;
import com.dj1012h.researchpilot.literature.review.ReviewEvidenceSerializer;
import com.dj1012h.researchpilot.literature.review.ReviewGenerationService;
import com.dj1012h.researchpilot.literature.review.ReviewInputBudgeter;
import com.dj1012h.researchpilot.literature.review.ReviewInputFactory;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import com.dj1012h.researchpilot.literature.review.UntrustedReviewDraft;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fixed offline release replay. Output is intentionally limited to safe
 * scenario names, statuses, counts, termination reasons, and citation counts.
 */
class TrustedDemoReplayTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldReplayThreeTrustedPathsWithRedactedSummaries() {
        replayFirstRoundTarget();
        replayRefinementTarget();
        replayInsufficientEvidence();
    }

    private void replayFirstRoundTarget() {
        ReplayHarness harness = new ReplayHarness();
        List<CandidatePaper> candidates = harness.candidates(1, 5);
        harness.stubOneRound(candidates);

        ReplayResult result = harness.execute(harness.plan("fixture", 5));

        assertThat(result.run().finalState().terminationReason())
                .isEqualTo(TerminationReason.TARGET_REACHED);
        assertThat(result.run().finalState().searchRoundCount()).isOne();
        assertThat(result.run().finalState().planAdjustmentCount()).isZero();
        assertThat(result.review().status()).isEqualTo(ReviewResponse.ReviewStatus.GENERATED);
        assertThat(result.review().citations()).hasSize(3);
        verify(harness.decider, never()).decide(any());
        emit("FIRST_ROUND_TARGET", result);
    }

    private void replayRefinementTarget() {
        ReplayHarness harness = new ReplayHarness();
        List<CandidatePaper> firstRound = harness.candidates(1, 2);
        List<CandidatePaper> secondRound = harness.candidates(3, 5);
        harness.stubTwoRounds(firstRound, secondRound, true);
        harness.stubRefinement();

        ReplayResult result = harness.execute(harness.plan("fixture", 5));

        assertThat(result.run().finalState().terminationReason())
                .isEqualTo(TerminationReason.TARGET_REACHED);
        assertThat(result.run().finalState().searchRoundCount()).isEqualTo(2);
        assertThat(result.run().finalState().planAdjustmentCount()).isOne();
        assertThat(result.review().status()).isEqualTo(ReviewResponse.ReviewStatus.GENERATED);
        assertThat(result.review().citations()).hasSize(3);
        verify(harness.refiner, times(1)).refine(any());
        emit("REFINEMENT_TARGET", result);
    }

    private void replayInsufficientEvidence() {
        ReplayHarness harness = new ReplayHarness();
        List<CandidatePaper> firstRound = harness.candidates(1, 1);
        List<CandidatePaper> secondRound = harness.candidates(2, 2);
        harness.stubTwoRounds(firstRound, secondRound, false);
        harness.stubRefinement();
        when(harness.decider.decide(any()))
                .thenReturn(new SearchActionDecision(
                        AgentAction.REFINE_PLAN, ActionDecisionSource.MODEL))
                .thenReturn(new SearchActionDecision(
                        AgentAction.COMPLETE, ActionDecisionSource.POLICY_SINGLE_ACTION));

        ReplayResult result = harness.execute(harness.plan("fixture", 5));

        assertThat(result.run().finalState().terminationReason())
                .isEqualTo(TerminationReason.NO_VERIFIED_RESULTS);
        assertThat(result.run().finalState().searchRoundCount()).isEqualTo(2);
        assertThat(result.run().finalState().verifiedPapers()).isEmpty();
        assertThat(result.review().status())
                .isEqualTo(ReviewResponse.ReviewStatus.INSUFFICIENT_EVIDENCE);
        assertThat(result.review().citations()).isEmpty();
        verify(harness.reviewGenerator, never()).generate(any());
        emit("INSUFFICIENT_EVIDENCE", result);
    }

    private void emit(String scenario, ReplayResult result) {
        AgentState state = result.run().finalState();
        int candidates = state.observations().stream()
                .filter(observation -> observation.action() == AgentAction.SEARCH_OPENALEX)
                .mapToInt(observation -> observation.candidateCount())
                .sum();
        long verified = state.verificationResults().stream()
                .filter(outcome -> outcome.verification().status()
                        == VerificationResult.VerificationStatus.VERIFIED)
                .count();
        String status = state.verifiedPapers().isEmpty()
                ? SearchResponse.SearchStatus.NO_VERIFIED_RESULTS.name()
                : state.verifiedPapers().size() >= state.requestedCount()
                        ? SearchResponse.SearchStatus.COMPLETED.name()
                        : SearchResponse.SearchStatus.PARTIAL_SUCCESS.name();

        System.out.printf(
                "[TRUSTED_DEMO_REPLAY] scenario=%s status=%s candidates=%d unique=%d "
                        + "verified=%d formal=%d termination=%s review=%s citations=%d%n",
                scenario,
                status,
                candidates,
                state.uniqueCandidateCount(),
                verified,
                state.verifiedPapers().size(),
                state.terminationReason(),
                result.review().status(),
                result.review().citations().size()
        );
    }

    private record ReplayResult(AgentRunResult run, ReviewResponse review) {
    }

    private static final class ReplayHarness {

        private final OpenAlexSearchPort openAlex = mock(OpenAlexSearchPort.class);
        private final CandidateDeduplicationService deduplication =
                mock(CandidateDeduplicationService.class);
        private final CrossrefCandidateLookupService crossref =
                mock(CrossrefCandidateLookupService.class);
        private final PaperVerificationService verification =
                mock(PaperVerificationService.class);
        private final EligiblePaperFilter eligible = mock(EligiblePaperFilter.class);
        private final SearchPlanRefiner refiner = mock(SearchPlanRefiner.class);
        private final SearchActionDecider decider = mock(SearchActionDecider.class);
        private final EvidenceReviewGenerator reviewGenerator =
                mock(EvidenceReviewGenerator.class);
        private final LiteratureResearchAgent agent;
        private final EvidenceReviewOrchestrator reviewOrchestrator;

        private ReplayHarness() {
            AgentBudgetProperties budgets = new AgentBudgetProperties();
            AgentBudgetPolicy policy = new AgentBudgetPolicy(budgets, CLOCK);
            AgentTransitionPolicy transitions = new AgentTransitionPolicy();
            SearchActionExecutor executor = new SearchActionExecutor(
                    transitions,
                    policy,
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
            agent = new LiteratureResearchAgent(
                    policy,
                    budgets,
                    new LiteratureSearchProperties(),
                    openAlex,
                    transitions,
                    decider,
                    executor,
                    new InMemoryExecutionTraceRecorder(),
                    CLOCK
            );
            when(reviewGenerator.generate(any())).thenReturn(new UntrustedReviewDraft("""
                    {
                      "statements": [
                        {
                          "type": "OBSERVATION",
                          "text": "The abstracts describe a bounded observation.",
                          "citationIds": ["P1", "P2", "P3"]
                        }
                      ]
                    }
                    """));
            reviewOrchestrator = reviewOrchestrator(reviewGenerator);
        }

        private ReplayResult execute(SearchPlan plan) {
            AgentRunResult run = agent.execute(
                    AgentState.initialize(
                            "offline acceptance fixture",
                            plan.resultLimit(),
                            CLOCK,
                            Duration.ofSeconds(90)
                    ),
                    validated(plan)
            );
            ReviewOutcome outcome = reviewOrchestrator.generateValidateAndAssemble(run);
            return new ReplayResult(run, new ReviewResponseAssembler().assemble(outcome));
        }

        private void stubOneRound(List<CandidatePaper> candidates) {
            CandidateDeduplicationResult deduplicated =
                    deduplication(candidates.toArray(CandidatePaper[]::new));
            CrossrefLookupSummary summary = availableSummary(candidates.size());
            List<CandidateVerificationOutcome> outcomes =
                    candidates.stream().map(this::verified).toList();
            when(openAlex.search(any())).thenReturn(
                    new OpenAlexSearchResult(candidates.size(), candidates, null));
            when(deduplication.deduplicate(candidates)).thenReturn(deduplicated);
            when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt()))
                    .thenReturn(summary);
            when(verification.verify(summary)).thenReturn(outcomes);
            when(eligible.filter(any(), anyInt()))
                    .thenReturn(candidates.stream().map(this::paper).toList());
        }

        private void stubTwoRounds(
                List<CandidatePaper> firstRound,
                List<CandidatePaper> secondRound,
                boolean verified
        ) {
            CandidateDeduplicationResult firstDedup =
                    deduplication(firstRound.toArray(CandidatePaper[]::new));
            CandidateDeduplicationResult secondDedup =
                    deduplication(secondRound.toArray(CandidatePaper[]::new));
            CrossrefLookupSummary firstSummary = availableSummary(firstRound.size());
            CrossrefLookupSummary secondSummary = availableSummary(secondRound.size());
            List<CandidateVerificationOutcome> firstOutcomes = firstRound.stream()
                    .map(candidate -> verified ? verified(candidate) : unverified(candidate))
                    .toList();
            List<CandidateVerificationOutcome> secondOutcomes = secondRound.stream()
                    .map(candidate -> verified ? verified(candidate) : unverified(candidate))
                    .toList();

            when(openAlex.search(any()))
                    .thenReturn(new OpenAlexSearchResult(firstRound.size(), firstRound, null))
                    .thenReturn(new OpenAlexSearchResult(secondRound.size(), secondRound, null));
            when(deduplication.deduplicate(firstRound)).thenReturn(firstDedup);
            when(deduplication.deduplicate(secondRound)).thenReturn(secondDedup);
            when(crossref.lookup(any(CandidateDeduplicationResult.class), anyInt()))
                    .thenReturn(firstSummary)
                    .thenReturn(secondSummary);
            when(verification.verify(firstSummary)).thenReturn(firstOutcomes);
            when(verification.verify(secondSummary)).thenReturn(secondOutcomes);
            if (verified) {
                List<SearchResponse.PaperResult> firstPapers =
                        firstRound.stream().map(this::paper).toList();
                List<SearchResponse.PaperResult> finalPapers =
                        Stream.concat(firstRound.stream(), secondRound.stream())
                                .map(this::paper)
                                .toList();
                when(eligible.filter(any(), anyInt())).thenAnswer(invocation -> {
                    List<?> outcomes = invocation.getArgument(0);
                    return outcomes.size() == firstRound.size()
                            ? firstPapers
                            : finalPapers;
                });
            } else {
                when(eligible.filter(any(), anyInt())).thenReturn(List.of());
            }
        }

        private void stubRefinement() {
            when(decider.decide(any())).thenReturn(new SearchActionDecision(
                    AgentAction.REFINE_PLAN,
                    ActionDecisionSource.MODEL
            ));
            SearchPlan refined = plan("fixture broadened", 5);
            when(refiner.refine(any())).thenReturn(new SearchPlanRefinementResult(
                    refined,
                    origins(),
                    new SearchPlanDiff(
                            List.of("broadened"),
                            List.of(),
                            List.of("ORIGINAL_QUERY", "FROM_YEAR", "TO_YEAR", "RESULT_LIMIT"),
                            "fixed offline refinement"),
                    1
            ));
        }

        private List<CandidatePaper> candidates(int first, int last) {
            return IntStream.rangeClosed(first, last)
                    .mapToObj(index -> candidate(
                            "W" + index,
                            "10.1000/fixture-" + index))
                    .toList();
        }

        private SearchPlan plan(String searchQuery, int resultLimit) {
            return new SearchPlan(
                    "offline acceptance fixture",
                    "offline acceptance",
                    List.of("fixture"),
                    searchQuery,
                    Set.of(LanguageCode.EN),
                    List.of("article"),
                    SearchSort.RELEVANCE,
                    2022,
                    2026,
                    15,
                    resultLimit
            );
        }

        private ValidatedSearchPlanContext validated(SearchPlan plan) {
            SearchRequest request = new SearchRequest(
                    plan.originalQuery(),
                    plan.fromYear(),
                    plan.toYear(),
                    plan.resultLimit()
            );
            SearchPlanGenerationContext generation = new SearchPlanGenerationContext(
                    UUID.fromString("00000000-0000-0000-0000-000000000041"),
                    request,
                    NOW,
                    2026
            );
            return new ValidatedSearchPlanContext(
                    generation,
                    new SearchPlanValidationResult(plan, origins())
            );
        }

        private SearchConstraintOrigins origins() {
            Map<SearchConstraintField, ConstraintOrigin> values =
                    Arrays.stream(SearchConstraintField.values()).collect(
                            Collectors.toMap(
                                    field -> field,
                                    field -> ConstraintOrigin.SYSTEM_FIXED
                            )
                    );
            return new SearchConstraintOrigins(values);
        }

        private CandidateDeduplicationResult deduplication(
                CandidatePaper... candidates
        ) {
            List<NormalizedCandidate> normalized = IntStream.range(0, candidates.length)
                    .mapToObj(index -> new NormalizedCandidate(
                            candidates[index].openAlexId(),
                            candidates[index],
                            candidates[index].doi(),
                            candidates[index].openAlexId(),
                            candidates[index].title().toLowerCase(),
                            null,
                            candidates[index].publicationYear(),
                            "fixture venue",
                            index
                    ))
                    .toList();
            return new CandidateDeduplicationResult(
                    normalized,
                    List.of(),
                    normalized.size(),
                    normalized.size(),
                    0
            );
        }

        private CandidatePaper candidate(String id, String doi) {
            return new CandidatePaper(
                    id,
                    doi,
                    "Fixture paper " + id,
                    List.of(),
                    "Fixture venue",
                    LocalDate.of(2026, 1, 1),
                    2026,
                    "article",
                    "en",
                    1,
                    null,
                    null,
                    null,
                    false,
                    CandidatePaper.CandidateSource.OPENALEX
            );
        }

        private CandidateVerificationOutcome verified(CandidatePaper candidate) {
            CrossrefWorkMetadata reference = new CrossrefWorkMetadata(
                    candidate.doi(),
                    candidate.title(),
                    List.of(),
                    2026,
                    "Fixture venue",
                    "article",
                    "Fixture publisher"
            );
            return new CandidateVerificationOutcome(
                    candidate,
                    reference,
                    verificationResult(
                            VerificationResult.VerificationStatus.VERIFIED,
                            candidate.doi()
                    )
            );
        }

        private CandidateVerificationOutcome unverified(CandidatePaper candidate) {
            return new CandidateVerificationOutcome(
                    candidate,
                    null,
                    verificationResult(
                            VerificationResult.VerificationStatus.NOT_FOUND,
                            null
                    )
            );
        }

        private VerificationResult verificationResult(
                VerificationResult.VerificationStatus status,
                String referenceDoi
        ) {
            return new VerificationResult(
                    status,
                    status == VerificationResult.VerificationStatus.VERIFIED
                            ? 1.0
                            : null,
                    VerificationResult.VerificationSource.CROSSREF,
                    referenceDoi,
                    List.of(),
                    List.of("OFFLINE_FIXTURE")
            );
        }

        private SearchResponse.PaperResult paper(CandidatePaper candidate) {
            PaperDTO dto = new PaperDTO(
                    candidate.openAlexId(),
                    candidate.doi(),
                    candidate.title(),
                    List.of(),
                    candidate.publicationYear(),
                    candidate.sourceName(),
                    List.of(),
                    candidate.workType(),
                    null,
                    "Fixed abstract evidence for release replay.",
                    candidate.language(),
                    List.of(),
                    candidate.citedByCount(),
                    PaperDTO.LiteratureSource.OPENALEX
            );
            return new SearchResponse.PaperResult(
                    dto,
                    1.0,
                    verificationResult(
                            VerificationResult.VerificationStatus.VERIFIED,
                            candidate.doi()
                    )
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

    private static EvidenceReviewOrchestrator reviewOrchestrator(
            EvidenceReviewGenerator generator
    ) {
        ReviewProperties properties = new ReviewProperties();
        StructuredOutputMapper mapper = new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper()
        );
        ReviewEvidenceSerializer serializer = new ReviewEvidenceSerializer(mapper);
        ReviewGenerationService generationService = new ReviewGenerationService(
                new ReviewInputFactory(new DoiNormalizer()),
                new EvidenceReviewPromptBuilder(serializer, properties),
                generator
        );
        ReviewDraftValidationPipeline pipeline = new ReviewDraftValidationPipeline(
                mapper,
                new ReviewDraftSchemaValidator(),
                new ReviewDraftMapper(mapper),
                new ReviewDraftBusinessValidator(),
                new CitationGuard(new CitationIdParser()),
                properties
        );
        return new EvidenceReviewOrchestrator(
                generationService,
                new ReviewInputBudgeter(properties, serializer),
                new EvidenceReviewRepairPromptBuilder(serializer, properties),
                pipeline,
                CLOCK
        );
    }
}
