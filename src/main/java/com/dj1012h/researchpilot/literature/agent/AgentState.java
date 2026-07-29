package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationKey;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.VerificationResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable aggregate for one controlled research task. Updates are explicit,
 * preserve counters across rounds, and never expose mutable collections.
 */
public record AgentState(
        String originalQuery,
        int requestedCount,
        SearchPlan currentPlan,
        List<SearchPlan> planHistory,
        AgentStage currentStage,
        AgentAction currentAction,
        List<CandidatePaper> retrievedCandidates,
        List<NormalizedCandidate> deduplicatedCandidates,
        List<CandidateVerificationOutcome> verificationResults,
        List<SearchResponse.PaperResult> verifiedPapers,
        int searchRoundCount,
        int planAdjustmentCount,
        int businessStepCount,
        int uniqueCandidateCount,
        int crossrefCallCount,
        Set<CandidateDeduplicationKey> globalCandidateKeys,
        int unkeyedUniqueCandidateCount,
        List<AgentObservation> observations,
        Instant startedAt,
        Instant deadline,
        Instant terminatedAt,
        TerminationReason terminationReason,
        String terminationDetail
) {
    public AgentState {
        originalQuery = requireText(originalQuery, "originalQuery");
        if (requestedCount < 1) throw new IllegalArgumentException("requestedCount must be positive");
        planHistory = List.copyOf(Objects.requireNonNull(planHistory, "planHistory must not be null"));
        currentStage = Objects.requireNonNull(currentStage, "currentStage must not be null");
        retrievedCandidates = List.copyOf(Objects.requireNonNull(retrievedCandidates, "retrievedCandidates must not be null"));
        deduplicatedCandidates = List.copyOf(Objects.requireNonNull(deduplicatedCandidates, "deduplicatedCandidates must not be null"));
        verificationResults = List.copyOf(Objects.requireNonNull(verificationResults, "verificationResults must not be null"));
        verifiedPapers = List.copyOf(Objects.requireNonNull(verifiedPapers, "verifiedPapers must not be null"));
        globalCandidateKeys = Set.copyOf(Objects.requireNonNull(globalCandidateKeys, "globalCandidateKeys must not be null"));
        observations = List.copyOf(Objects.requireNonNull(observations, "observations must not be null"));
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        if (!deadline.isAfter(startedAt)) throw new IllegalArgumentException("deadline must be after startedAt");
        if (searchRoundCount < 0 || planAdjustmentCount < 0 || businessStepCount < 0
                || uniqueCandidateCount < 0 || crossrefCallCount < 0 || unkeyedUniqueCandidateCount < 0) {
            throw new IllegalArgumentException("state counters must not be negative");
        }
        if (uniqueCandidateCount != globalCandidateKeys.size() + unkeyedUniqueCandidateCount) {
            throw new IllegalArgumentException("uniqueCandidateCount must equal keyed plus unkeyed candidate counts");
        }
        if (terminationReason == null && (terminatedAt != null || terminationDetail != null || currentStage == AgentStage.TERMINATED)) {
            throw new IllegalArgumentException("termination metadata requires a termination reason");
        }
        if (terminationReason != null && (terminatedAt == null
                || (currentStage != AgentStage.TERMINATED && currentStage != AgentStage.COMPLETED))) {
            throw new IllegalArgumentException("terminated state requires timestamp and terminal stage");
        }
    }

    public static AgentState initialize(String originalQuery, int requestedCount, Clock clock, java.time.Duration timeout) {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        Instant startedAt = Instant.now(clock);
        return new AgentState(originalQuery, requestedCount, null, List.of(), AgentStage.INITIALIZED, null,
                List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0, 0, Set.of(), 0,
                List.of(), startedAt, startedAt.plus(timeout), null, null, null);
    }

    public boolean terminated() { return terminationReason != null; }

    public AgentState recordInitialPlan(SearchPlan plan) {
        assertActive();
        plan = Objects.requireNonNull(plan, "plan must not be null");
        if (!originalQuery.equals(plan.originalQuery())) {
            throw new IllegalArgumentException("trusted plan must preserve originalQuery");
        }
        return copy(plan, append(planHistory, plan), AgentStage.PLAN_READY, currentAction,
                retrievedCandidates, deduplicatedCandidates, verificationResults, verifiedPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount, uniqueCandidateCount, crossrefCallCount,
                globalCandidateKeys, unkeyedUniqueCandidateCount, observations, null, null, null);
    }

    public AgentState startAction(ActionExecutionPermit permit) {
        assertActive();
        Objects.requireNonNull(permit, "permit must not be null");
        AgentAction action = permit.action();
        int nextSteps = businessStepCount + (action.countsAsBusinessStep() ? 1 : 0);
        int nextSearchRounds = searchRoundCount + (action == AgentAction.SEARCH_OPENALEX ? 1 : 0);
        int nextPlanAdjustments = planAdjustmentCount + (action == AgentAction.REFINE_PLAN ? 1 : 0);
        return copy(currentPlan, planHistory, stageForStart(action), action,
                retrievedCandidates, deduplicatedCandidates, verificationResults, verifiedPapers,
                nextSearchRounds, nextPlanAdjustments, nextSteps, uniqueCandidateCount, crossrefCallCount,
                globalCandidateKeys, unkeyedUniqueCandidateCount, observations, null, null, null);
    }

    public AgentState recordSearchResult(List<CandidatePaper> candidates) {
        assertActive();
        if (currentAction != AgentAction.SEARCH_OPENALEX) {
            throw new IllegalStateException("search results require SEARCH_OPENALEX as current action");
        }
        return copy(currentPlan, planHistory, AgentStage.CANDIDATES_RETRIEVED, currentAction,
                candidates, deduplicatedCandidates, verificationResults, verifiedPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount, uniqueCandidateCount, crossrefCallCount,
                globalCandidateKeys, unkeyedUniqueCandidateCount, observations, null, null, null);
    }

    public AgentState recordDeduplicatedCandidates(CandidateDeduplicationResult result, ActionExecutionPermit permit) {
        assertActive();
        Objects.requireNonNull(result, "result must not be null");
        requirePermit(permit, AgentAction.DEDUPLICATE_CANDIDATES);
        Set<CandidateDeduplicationKey> nextKeys = new LinkedHashSet<>(globalCandidateKeys);
        int newUnkeyed = 0;
        for (NormalizedCandidate candidate : result.uniqueCandidates()) {
            var key = CandidateDeduplicationKey.from(candidate);
            if (key.isPresent()) {
                nextKeys.add(key.get());
            } else {
                newUnkeyed++;
            }
        }
        int actualNew = nextKeys.size() - globalCandidateKeys.size() + newUnkeyed;
        if (actualNew > permit.estimatedCost().uniqueCandidates()) {
            throw new IllegalArgumentException("actual unique candidates exceed the checked estimate");
        }
        return copy(currentPlan, planHistory, AgentStage.CANDIDATES_DEDUPLICATED, currentAction,
                retrievedCandidates, result.uniqueCandidates(), verificationResults, verifiedPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount,
                uniqueCandidateCount + actualNew, crossrefCallCount, nextKeys,
                unkeyedUniqueCandidateCount + newUnkeyed, observations, null, null, null);
    }

    public AgentState recordVerificationResults(List<CandidateVerificationOutcome> results,
                                                List<SearchResponse.PaperResult> formalPapers) {
        assertActive();
        if (currentAction != AgentAction.VERIFY_WITH_CROSSREF) {
            throw new IllegalStateException("verification results require VERIFY_WITH_CROSSREF as current action");
        }
        results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
        formalPapers = List.copyOf(Objects.requireNonNull(formalPapers, "formalPapers must not be null"));
        if (formalPapers.stream().anyMatch(paper -> paper.verification().status()
                != VerificationResult.VerificationStatus.VERIFIED)) {
            throw new IllegalArgumentException("formal papers must be VERIFIED");
        }
        return copy(currentPlan, planHistory, AgentStage.VERIFICATION_COMPLETED, currentAction,
                retrievedCandidates, deduplicatedCandidates, results, formalPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount, uniqueCandidateCount, crossrefCallCount,
                globalCandidateKeys, unkeyedUniqueCandidateCount, observations, null, null, null);
    }

    public AgentState recordRefinedPlan(SearchPlan plan, ActionExecutionPermit permit) {
        assertActive();
        requirePermit(permit, AgentAction.REFINE_PLAN);
        plan = Objects.requireNonNull(plan, "plan must not be null");
        if (!originalQuery.equals(plan.originalQuery())) {
            throw new IllegalArgumentException("refined plan must preserve originalQuery");
        }
        return copy(plan, append(planHistory, plan), AgentStage.PLAN_READY, currentAction,
                retrievedCandidates, deduplicatedCandidates, verificationResults, verifiedPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount, uniqueCandidateCount, crossrefCallCount,
                globalCandidateKeys, unkeyedUniqueCandidateCount, observations, null, null, null);
    }

    public AgentState recordObservation(AgentObservation observation, ActionExecutionPermit permit) {
        assertActive();
        observation = Objects.requireNonNull(observation, "observation must not be null");
        requirePermit(permit, observation.action());
        if (observation.crossrefCallsUsed() > permit.estimatedCost().crossrefCalls()) {
            throw new IllegalArgumentException("actual Crossref calls exceed the checked estimate");
        }
        return copy(currentPlan, planHistory, observation.stageAfter(), currentAction,
                retrievedCandidates, deduplicatedCandidates, verificationResults, verifiedPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount, uniqueCandidateCount,
                crossrefCallCount + observation.crossrefCallsUsed(), globalCandidateKeys,
                unkeyedUniqueCandidateCount, append(observations, observation), null, null, null);
    }

    public AgentState terminate(TerminationReason reason, String detail, Instant at) {
        if (terminated()) return this;
        return copy(currentPlan, planHistory, AgentStage.TERMINATED, AgentAction.TERMINATE,
                retrievedCandidates, deduplicatedCandidates, verificationResults, verifiedPapers,
                searchRoundCount, planAdjustmentCount, businessStepCount, uniqueCandidateCount, crossrefCallCount,
                globalCandidateKeys, unkeyedUniqueCandidateCount, observations,
                Objects.requireNonNull(at, "at must not be null"), Objects.requireNonNull(reason, "reason must not be null"),
                requireText(detail, "detail"));
    }

    public AgentState complete(TerminationReason reason, String detail, Instant at) {
        if (terminated()) throw new IllegalStateException("terminated state cannot be completed");
        if (reason != TerminationReason.TARGET_REACHED && reason != TerminationReason.PARTIAL_RESULTS
                && reason != TerminationReason.NO_VERIFIED_RESULTS) {
            throw new IllegalArgumentException("completion requires a result reason");
        }
        AgentState terminated = terminate(reason, detail, at);
        return terminated.copy(terminated.currentPlan, terminated.planHistory, AgentStage.COMPLETED, AgentAction.COMPLETE,
                terminated.retrievedCandidates, terminated.deduplicatedCandidates, terminated.verificationResults,
                terminated.verifiedPapers, terminated.searchRoundCount, terminated.planAdjustmentCount,
                terminated.businessStepCount, terminated.uniqueCandidateCount, terminated.crossrefCallCount,
                terminated.globalCandidateKeys, terminated.unkeyedUniqueCandidateCount, terminated.observations,
                terminated.terminatedAt, terminated.terminationReason, terminated.terminationDetail);
    }

    private AgentState copy(SearchPlan plan, List<SearchPlan> history, AgentStage stage, AgentAction action,
                            List<CandidatePaper> retrieved, List<NormalizedCandidate> deduplicated,
                            List<CandidateVerificationOutcome> verification, List<SearchResponse.PaperResult> formal,
                            int searchRounds, int adjustments, int steps, int unique, int crossref,
                            Set<CandidateDeduplicationKey> keys, int unkeyed, List<AgentObservation> nextObservations,
                            Instant nextTerminatedAt, TerminationReason nextReason, String nextDetail) {
        return new AgentState(originalQuery, requestedCount, plan, history, stage, action, retrieved, deduplicated,
                verification, formal, searchRounds, adjustments, steps, unique, crossref, keys, unkeyed,
                nextObservations, startedAt, deadline, nextTerminatedAt, nextReason, nextDetail);
    }

    private void assertActive() {
        if (terminated()) throw new IllegalStateException("terminated state cannot accept further actions");
    }
    private void requirePermit(ActionExecutionPermit permit, AgentAction expected) {
        Objects.requireNonNull(permit, "permit must not be null");
        if (permit.action() != expected || currentAction != expected) {
            throw new IllegalStateException("operation requires an active permit for " + expected);
        }
    }
    private AgentStage stageForStart(AgentAction action) {
        return switch (action) {
            case SEARCH_OPENALEX -> AgentStage.SEARCHING;
            case DEDUPLICATE_CANDIDATES -> AgentStage.DEDUPLICATING;
            case VERIFY_WITH_CROSSREF -> AgentStage.VERIFYING;
            case EVALUATE_RESULTS -> AgentStage.EVALUATING_RESULTS;
            default -> currentStage;
        };
    }
    private static <T> List<T> append(List<T> values, T value) {
        List<T> copied = new ArrayList<>(values);
        copied.add(value);
        return List.copyOf(copied);
    }
    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
