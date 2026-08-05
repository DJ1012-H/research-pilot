package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.agent.AgentAction;
import com.dj1012h.researchpilot.literature.agent.AgentObservation;
import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentStage;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.agent.LiteratureResearchAgent;
import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.PublicTerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceFacade;
import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceException;
import com.dj1012h.researchpilot.literature.review.EvidenceReviewOrchestrator;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import com.dj1012h.researchpilot.literature.review.ReviewOutcomeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LiteratureSearchServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-01T08:00:00Z"), ZoneOffset.UTC);

    private final SearchAgent searchAgent = mock(SearchAgent.class);
    private final LiteratureResearchAgent literatureResearchAgent = mock(LiteratureResearchAgent.class);
    private final EvidenceReviewOrchestrator evidenceReviewOrchestrator =
            mock(EvidenceReviewOrchestrator.class);
    private final LiteratureSearchService service =
            new LiteratureSearchService(
                    searchAgent,
                    literatureResearchAgent,
                    evidenceReviewOrchestrator,
                    new ReviewResponseAssembler(),
                    new PublicTerminationReasonMapper(),
                    CLOCK
            );

    @Test
    void shouldDelegateTheTrustedPlanAndFiniteExecutionToTheResearchAgent() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", null, null, 5);
        SearchPlan plan = plan(request);
        ValidatedSearchPlanContext context = mock(ValidatedSearchPlanContext.class);
        AgentState initialState = mock(AgentState.class);
        AgentRunResult runResult = runResult(plan, List.of(), List.of(), 0, 0,
                TerminationReason.NO_VERIFIED_RESULTS);
        when(searchAgent.createPlanContext(request)).thenReturn(context);
        when(literatureResearchAgent.initialize(request)).thenReturn(initialState);
        when(literatureResearchAgent.execute(initialState, context)).thenReturn(runResult);

        SearchResponse response = service.search(request);

        assertThat(response.status()).isEqualTo(SearchResponse.SearchStatus.NO_VERIFIED_RESULTS);
        assertThat(response.plan()).isSameAs(plan);
        assertThat(response.candidateCount()).isZero();
        assertThat(response.deduplicatedCount()).isZero();
        assertThat(response.verificationSummary().totalCount()).isZero();
        assertThat(response.papers()).isEmpty();
        assertThat(response.review().status())
                .isEqualTo(ReviewResponse.ReviewStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.terminationReason())
                .isEqualTo(PublicTerminationReason.NO_VERIFIED_RESULTS);
        assertThat(response.message()).isEqualTo("未找到满足最低核验标准的论文。");
        assertThat(response.elapsedMs()).isZero();
        assertThat(response.completedAt()).isEqualTo(Instant.parse("2026-08-01T08:00:00Z"));
        assertThat(response.taskId()).isNotNull();
        verify(searchAgent).createPlanContext(request);
        verify(literatureResearchAgent).initialize(request);
        verify(literatureResearchAgent).execute(initialState, context);
        verifyNoMoreInteractions(literatureResearchAgent);
    }

    @Test
    void shouldMapBudgetLimitedVerifiedResultsToPartialSuccessAndSumAllSearchRounds() {
        SearchRequest request = new SearchRequest("Mamba", null, null, 5);
        SearchPlan plan = plan(request);
        SearchResponse.PaperResult paper = paper();
        List<AgentObservation> observations = List.of(
                observation(AgentAction.SEARCH_OPENALEX, 10),
                observation(AgentAction.DEDUPLICATE_CANDIDATES, 0),
                observation(AgentAction.SEARCH_OPENALEX, 8)
        );
        AgentRunResult runResult = runResult(plan, List.of(paper), observations, 8, 1,
                TerminationReason.STEP_LIMIT_REACHED);
        when(searchAgent.createPlanContext(request)).thenReturn(mock(ValidatedSearchPlanContext.class));
        when(literatureResearchAgent.initialize(request)).thenReturn(mock(AgentState.class));
        when(literatureResearchAgent.execute(any(), any())).thenReturn(runResult);

        SearchResponse response = service.search(request);

        assertThat(response.status()).isEqualTo(SearchResponse.SearchStatus.PARTIAL_SUCCESS);
        assertThat(response.papers()).containsExactly(paper);
        assertThat(response.candidateCount()).isEqualTo(18);
        assertThat(response.deduplicatedCount()).isEqualTo(8);
        assertThat(response.verificationSummary().verifiedCount()).isOne();
        assertThat(response.verificationSummary().totalCount()).isEqualTo(8);
        assertThat(response.terminationReason()).isEqualTo(PublicTerminationReason.LIMIT_REACHED);
        assertThat(response.message()).contains("已达到执行步骤上限");
    }

    @Test
    void shouldMapEnoughVerifiedResultsToCompletedWithoutExposingTheTrace() {
        SearchRequest request = new SearchRequest("Mamba", null, null, 1);
        SearchPlan plan = plan(request);
        SearchResponse.PaperResult paper = paper();
        AgentRunResult runResult = runResult(plan, List.of(paper),
                List.of(observation(AgentAction.SEARCH_OPENALEX, 1)), 1, 1,
                TerminationReason.TARGET_REACHED);
        when(searchAgent.createPlanContext(request)).thenReturn(mock(ValidatedSearchPlanContext.class));
        when(literatureResearchAgent.initialize(request)).thenReturn(mock(AgentState.class));
        when(literatureResearchAgent.execute(any(), any())).thenReturn(runResult);

        SearchResponse response = service.search(request);

        assertThat(response.status()).isEqualTo(SearchResponse.SearchStatus.COMPLETED);
        assertThat(response.papers()).containsExactly(paper);
        assertThat(response.message()).isEqualTo("已找到并核验通过 1 篇论文。");
        assertThat(SearchResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("review", "terminationReason")
                .doesNotContain("trace", "terminationDetail", "agentState");
    }

    @Test
    void shouldIncludeReviewStageTimeInCompletedAtAndElapsedMs() {
        Instant startedAt = Instant.parse("2026-08-01T08:00:00Z");
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant())
                .thenReturn(startedAt, startedAt.plusSeconds(5));
        LiteratureSearchService timedService = new LiteratureSearchService(
                searchAgent,
                literatureResearchAgent,
                evidenceReviewOrchestrator,
                new ReviewResponseAssembler(),
                new PublicTerminationReasonMapper(),
                advancingClock
        );
        SearchRequest request = new SearchRequest("Mamba", null, null, 5);
        AgentRunResult runResult = runResult(
                plan(request),
                List.of(),
                List.of(),
                0,
                0,
                TerminationReason.NO_VERIFIED_RESULTS
        );
        when(searchAgent.createPlanContext(request))
                .thenReturn(mock(ValidatedSearchPlanContext.class));
        when(literatureResearchAgent.initialize(request)).thenReturn(mock(AgentState.class));
        when(literatureResearchAgent.execute(any(), any())).thenReturn(runResult);

        SearchResponse response = timedService.search(request);

        assertThat(response.elapsedMs()).isEqualTo(5_000);
        assertThat(response.completedAt()).isEqualTo(startedAt.plusSeconds(5));
    }

    @Test
    void shouldMeasureTaskPersistenceWithoutLoggingRequestContent(CapturedOutput output) {
        LiteraturePersistenceFacade persistence = mock(LiteraturePersistenceFacade.class);
        LiteratureSearchService persistentService = new LiteratureSearchService(
                searchAgent,
                literatureResearchAgent,
                evidenceReviewOrchestrator,
                new ReviewResponseAssembler(),
                new PublicTerminationReasonMapper(),
                persistence,
                CLOCK
        );
        String privateQuery = "private-query-must-not-appear";
        SearchRequest request = new SearchRequest(privateQuery, null, null, 5);
        ValidatedSearchPlanContext context = mock(ValidatedSearchPlanContext.class);
        AgentState initialState = mock(AgentState.class);
        when(initialState.requestedCount()).thenReturn(5);
        AgentRunResult runResult = runResult(
                plan(request),
                List.of(),
                List.of(),
                0,
                0,
                TerminationReason.NO_VERIFIED_RESULTS
        );
        when(searchAgent.createPlanContext(request)).thenReturn(context);
        when(literatureResearchAgent.initialize(request)).thenReturn(initialState);
        when(literatureResearchAgent.execute(any(), any(), any())).thenReturn(runResult);

        SearchResponse response = persistentService.search(request);

        verify(persistence).createRunningTask(
                response.taskId(), request, 5, Instant.parse("2026-08-01T08:00:00Z")
        );
        verify(persistence).finalizeSuccess(
                response.taskId(), runResult, ReviewOutcome.failed(
                        ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE,
                        0,
                        0,
                        0,
                        "TEST_INSUFFICIENT_EVIDENCE"
                ), Instant.parse("2026-08-01T08:00:00Z")
        );
        assertThat(output)
                .contains("event=literature_persistence")
                .contains("operation=CREATE_RUNNING_TASK")
                .contains("operation=FINALIZE_SUCCESS")
                .contains("outcome=SUCCEEDED")
                .containsPattern("durationMs=\\d+")
                .doesNotContain(privateQuery);
    }

    @Test
    void shouldWrapCreateTaskFailureAsStablePersistenceInfrastructureFailure() {
        LiteraturePersistenceFacade persistence = mock(LiteraturePersistenceFacade.class);
        LiteratureSearchService persistentService = new LiteratureSearchService(
                searchAgent, literatureResearchAgent, evidenceReviewOrchestrator,
                new ReviewResponseAssembler(), new PublicTerminationReasonMapper(), persistence, CLOCK);
        SearchRequest request = new SearchRequest("offline query", null, null, 5);
        AgentState initialState = mock(AgentState.class);
        when(initialState.requestedCount()).thenReturn(5);
        when(literatureResearchAgent.initialize(request)).thenReturn(initialState);
        doThrow(new IllegalStateException("jdbc:mysql://private-host/example?password=SENSITIVE_TOKEN_8A4F"))
                .when(persistence).createRunningTask(any(), any(), anyInt(), any());

        assertThatThrownBy(() -> persistentService.search(request))
                .isInstanceOf(LiteraturePersistenceException.class)
                .hasMessage("literature persistence operation failed")
                .hasRootCauseInstanceOf(IllegalStateException.class);
        verifyNoMoreInteractions(searchAgent);
    }

    private AgentRunResult runResult(
            SearchPlan plan,
            List<SearchResponse.PaperResult> papers,
            List<AgentObservation> observations,
            int uniqueCandidateCount,
            int verifiedCount,
            TerminationReason terminationReason
    ) {
        AgentState state = mock(AgentState.class);
        when(state.currentPlan()).thenReturn(plan);
        when(state.requestedCount()).thenReturn(plan.resultLimit());
        when(state.verifiedPapers()).thenReturn(papers);
        when(state.observations()).thenReturn(observations);
        when(state.uniqueCandidateCount()).thenReturn(uniqueCandidateCount);
        when(state.crossrefCallCount()).thenReturn(uniqueCandidateCount);
        when(state.currentStage()).thenReturn(AgentStage.COMPLETED);
        when(state.terminationReason()).thenReturn(terminationReason);
        List<com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome> verificationResults =
                verificationResults(verifiedCount, uniqueCandidateCount);
        when(state.verificationResults()).thenReturn(verificationResults);
        AgentRunResult runResult = mock(AgentRunResult.class);
        when(runResult.finalState()).thenReturn(state);
        when(evidenceReviewOrchestrator.generateValidateAndAssemble(runResult))
                .thenReturn(ReviewOutcome.failed(
                        ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE,
                        0,
                        0,
                        0,
                        "TEST_INSUFFICIENT_EVIDENCE"
                ));
        return runResult;
    }

    private List<com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome> verificationResults(
            int verifiedCount,
            int totalCount
    ) {
        return java.util.stream.IntStream.range(0, totalCount)
                .mapToObj(index -> {
                    var outcome = mock(com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome.class);
                    VerificationResult verification = new VerificationResult(
                            index < verifiedCount ? VerificationResult.VerificationStatus.VERIFIED
                                    : VerificationResult.VerificationStatus.NOT_CHECKED,
                            0.0,
                            VerificationResult.VerificationSource.CROSSREF,
                            null,
                            List.of(),
                            List.of("TEST")
                    );
                    when(outcome.verification()).thenReturn(verification);
                    return outcome;
                })
                .toList();
    }

    private AgentObservation observation(AgentAction action, int candidateCount) {
        return new AgentObservation(
                action, AgentStage.PLAN_READY, AgentStage.PLAN_READY, true,
                candidateCount, 0, 0, 0, 0, Duration.ZERO,
                "offline test observation", null, Instant.parse("2026-08-01T08:00:00Z")
        );
    }

    private SearchResponse.PaperResult paper() {
        VerificationResult verification = new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED,
                1.0,
                VerificationResult.VerificationSource.CROSSREF,
                "10.1000/example",
                List.of(),
                List.of("TEST")
        );
        PaperDTO dto = new PaperDTO(
                "W1", "10.1000/example", "Example paper", List.of(), 2026, "Example Journal",
                List.of(), "article", null, null, "en", List.of(), 1, PaperDTO.LiteratureSource.OPENALEX
        );
        return new SearchResponse.PaperResult(dto, 1.0, verification);
    }

    private SearchPlan plan(SearchRequest request) {
        return new SearchPlan(
                request.query(), "Mamba remote sensing change detection",
                List.of("Mamba", "remote sensing", "change detection"),
                "Mamba remote sensing change detection", Set.of(LanguageCode.EN), List.of("article"),
                SearchSort.NEWEST, 2022, 2026, 30, request.limit()
        );
    }
}
