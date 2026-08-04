package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.agent.AgentAction;
import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.agent.LiteratureResearchAgent;
import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceFacade;
import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceException;
import com.dj1012h.researchpilot.literature.persistence.NoOpLiteraturePersistenceFacade;
import com.dj1012h.researchpilot.literature.review.EvidenceReviewOrchestrator;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Executes the trusted retrieval, Crossref verification, and formal-output chain. */
@Service
public class LiteratureSearchService {

    private static final Logger log = LoggerFactory.getLogger(LiteratureSearchService.class);

    private final SearchAgent searchAgent;
    private final LiteratureResearchAgent literatureResearchAgent;
    private final EvidenceReviewOrchestrator evidenceReviewOrchestrator;
    private final ReviewResponseAssembler reviewResponseAssembler;
    private final PublicTerminationReasonMapper terminationReasonMapper;
    private final LiteraturePersistenceFacade persistence;
    private final Clock clock;

    public LiteratureSearchService(
            SearchAgent searchAgent,
            LiteratureResearchAgent literatureResearchAgent,
            EvidenceReviewOrchestrator evidenceReviewOrchestrator,
            ReviewResponseAssembler reviewResponseAssembler,
            PublicTerminationReasonMapper terminationReasonMapper,
            Clock clock
    ) {
        this(searchAgent, literatureResearchAgent, evidenceReviewOrchestrator, reviewResponseAssembler,
                terminationReasonMapper, NoOpLiteraturePersistenceFacade.INSTANCE, clock);
    }

    @Autowired
    public LiteratureSearchService(
            SearchAgent searchAgent,
            LiteratureResearchAgent literatureResearchAgent,
            EvidenceReviewOrchestrator evidenceReviewOrchestrator,
            ReviewResponseAssembler reviewResponseAssembler,
            PublicTerminationReasonMapper terminationReasonMapper,
            LiteraturePersistenceFacade persistence,
            Clock clock
    ) {
        this.searchAgent = Objects.requireNonNull(searchAgent, "searchAgent must not be null");
        this.literatureResearchAgent = Objects.requireNonNull(
                literatureResearchAgent, "literatureResearchAgent must not be null");
        this.evidenceReviewOrchestrator = Objects.requireNonNull(
                evidenceReviewOrchestrator, "evidenceReviewOrchestrator must not be null");
        this.reviewResponseAssembler = Objects.requireNonNull(
                reviewResponseAssembler, "reviewResponseAssembler must not be null");
        this.terminationReasonMapper = Objects.requireNonNull(
                terminationReasonMapper, "terminationReasonMapper must not be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SearchResponse search(SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID taskId = UUID.randomUUID();
        Instant startedAt = Instant.now(clock);
        boolean persistenceEnabled = !(persistence instanceof NoOpLiteraturePersistenceFacade);
        boolean runningTaskCreated = false;
        try {
            AgentState initialState;
            ValidatedSearchPlanContext initialPlanContext;
            if (persistenceEnabled) {
                initialState = literatureResearchAgent.initialize(request);
                recordPersistence(
                        "CREATE_RUNNING_TASK",
                        () -> persistence.createRunningTask(
                                taskId, request, initialState.requestedCount(), startedAt)
                );
                runningTaskCreated = true;
                initialPlanContext = searchAgent.createPlanContext(request);
            } else {
                initialPlanContext = searchAgent.createPlanContext(request);
                initialState = literatureResearchAgent.initialize(request);
            }
            AgentRunResult runResult = persistenceEnabled
                    ? literatureResearchAgent.execute(initialState, initialPlanContext, taskId)
                    : literatureResearchAgent.execute(initialState, initialPlanContext);
            AgentState finalState = runResult.finalState();
            SearchPlan finalPlan = finalState.currentPlan();
            List<SearchResponse.PaperResult> papers = finalState.verifiedPapers();
            SearchResponse.VerificationSummary verificationSummary = verificationSummary(finalState.verificationResults());
            ReviewOutcome reviewOutcome = evidenceReviewOrchestrator.generateValidateAndAssemble(runResult);
            ReviewResponse reviewResponse = reviewResponseAssembler.assemble(reviewOutcome);
            var publicTerminationReason = terminationReasonMapper.toPublic(finalState.terminationReason());

            Instant completedAt = Instant.now(clock);
            long elapsedMs = Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli());
            SearchResponse response = new SearchResponse(
                    taskId,
                    status(papers, finalState.requestedCount()),
                    finalPlan,
                    totalRetrievedCandidates(finalState),
                    finalState.uniqueCandidateCount(),
                    verificationSummary,
                    papers,
                    reviewResponse,
                    publicTerminationReason,
                    message(papers.size(), finalState.requestedCount(), finalState.terminationReason()),
                    elapsedMs,
                    completedAt
            );
            if (persistenceEnabled) {
                recordPersistence(
                        "FINALIZE_SUCCESS",
                        () -> persistence.finalizeSuccess(taskId, runResult, reviewOutcome, completedAt)
                );
            } else {
                persistence.finalizeSuccess(taskId, runResult, reviewOutcome, completedAt);
            }

            log.info(
                "event=literature_search_completed taskId={} agentStage={} terminationReason={} "
                        + "candidateCount={} uniqueCandidateCount={} crossrefAttemptedCount={} "
                        + "verifiedCount={} formalResultCount={} reviewStatus={} "
                        + "reviewModelCallCount={} reviewRepairCount={} reviewEvidenceCount={} "
                        + "reviewCitationCount={} elapsedMs={}",
                taskId, finalState.currentStage(), publicTerminationReason, totalRetrievedCandidates(finalState),
                finalState.uniqueCandidateCount(), finalState.crossrefCallCount(),
                verificationSummary.verifiedCount(), papers.size(), reviewOutcome.status(),
                reviewOutcome.modelCallCount(), reviewOutcome.repairCount(), reviewOutcome.evidenceCount(),
                reviewResponse.citations().size(), elapsedMs
            );
            return response;
        } catch (RuntimeException exception) {
            if (runningTaskCreated) {
                try {
                    recordPersistence(
                            "FINALIZE_FAILURE",
                            () -> persistence.finalizeFailure(
                                    taskId, stableFailureCode(exception), Instant.now(clock))
                    );
                } catch (RuntimeException persistenceFailure) {
                    LiteraturePersistenceException infrastructureFailure = new LiteraturePersistenceException(
                            "could not persist the task failure state", persistenceFailure);
                    infrastructureFailure.addSuppressed(exception);
                    throw infrastructureFailure;
                }
            }
            throw exception;
        }
    }

    private void recordPersistence(String operation, Runnable persistenceCall) {
        long startedAt = System.nanoTime();
        try {
            persistenceCall.run();
            log.info(
                    "event=literature_persistence operation={} outcome=SUCCEEDED durationMs={}",
                    operation, elapsedMillis(startedAt)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event=literature_persistence operation={} outcome=FAILED durationMs={} exceptionType={}",
                    operation, elapsedMillis(startedAt), exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    private String stableFailureCode(RuntimeException exception) {
        String name = exception.getClass().getSimpleName();
        return name.isBlank() ? "UNEXPECTED_FAILURE" : name;
    }

    private SearchResponse.SearchStatus status(List<SearchResponse.PaperResult> papers, int resultLimit) {
        if (papers.isEmpty()) return SearchResponse.SearchStatus.NO_VERIFIED_RESULTS;
        return papers.size() < resultLimit
                ? SearchResponse.SearchStatus.PARTIAL_SUCCESS
                : SearchResponse.SearchStatus.COMPLETED;
    }

    private int totalRetrievedCandidates(AgentState state) {
        return state.observations().stream()
                .filter(observation -> observation.action() == AgentAction.SEARCH_OPENALEX)
                .mapToInt(observation -> observation.candidateCount())
                .sum();
    }

    private SearchResponse.VerificationSummary verificationSummary(List<CandidateVerificationOutcome> outcomes) {
        int verified = 0;
        int partial = 0;
        int unverified = 0;
        int rejected = 0;
        for (CandidateVerificationOutcome outcome : outcomes) {
            switch (outcome.verification().status()) {
                case VERIFIED -> verified++;
                case PARTIALLY_VERIFIED -> partial++;
                case NOT_CHECKED, NOT_FOUND, SOURCE_UNAVAILABLE -> unverified++;
                case CONFLICTED, REJECTED -> rejected++;
            }
        }
        return new SearchResponse.VerificationSummary(verified, partial, unverified, rejected);
    }

    private String message(int formalResultCount, int requestedCount, TerminationReason terminationReason) {
        String base = switch (statusForMessage(formalResultCount, requestedCount)) {
            case COMPLETED -> "已找到并核验通过 " + formalResultCount + " 篇论文。";
            case PARTIAL_SUCCESS -> "计划返回 " + requestedCount + " 篇，实际核验通过 "
                    + formalResultCount + " 篇；已返回当前可信结果。";
            case NO_VERIFIED_RESULTS -> "未找到满足最低核验标准的论文。";
        };
        return base + terminationSuffix(terminationReason);
    }

    private SearchResponse.SearchStatus statusForMessage(int formalResultCount, int requestedCount) {
        if (formalResultCount == 0) return SearchResponse.SearchStatus.NO_VERIFIED_RESULTS;
        return formalResultCount < requestedCount
                ? SearchResponse.SearchStatus.PARTIAL_SUCCESS
                : SearchResponse.SearchStatus.COMPLETED;
    }

    private String terminationSuffix(TerminationReason reason) {
        if (reason == null) return "";
        return switch (reason) {
            case SEARCH_ROUND_LIMIT_REACHED -> " 已达到检索轮次上限。";
            case PLAN_ADJUSTMENT_LIMIT_REACHED -> " 已达到计划调整上限。";
            case STEP_LIMIT_REACHED -> " 已达到执行步骤上限。";
            case CANDIDATE_BUDGET_EXHAUSTED -> " 已达到候选数量上限。";
            case CROSSREF_BUDGET_EXHAUSTED -> " 已达到核验调用上限。";
            case DEADLINE_EXCEEDED -> " 已达到执行时间上限。";
            case EXTERNAL_SERVICE_UNAVAILABLE -> " 外部核验服务当前不可用。";
            case INVALID_STATE, UNEXPECTED_FAILURE -> " 执行已安全终止。";
            case TARGET_REACHED, PARTIAL_RESULTS, NO_VERIFIED_RESULTS -> "";
        };
    }
}
