package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.CandidateDeduplicationService;
import com.dj1012h.researchpilot.literature.application.CrossrefCandidateLookupService;
import com.dj1012h.researchpilot.literature.application.CrossrefLookupSummary;
import com.dj1012h.researchpilot.literature.application.EligiblePaperFilter;
import com.dj1012h.researchpilot.literature.application.OpenAlexQueryFactory;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationKey;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes one already selected action through the structural and budget gates. */
@Component
public class SearchActionExecutor {

    private final AgentTransitionPolicy transitionPolicy;
    private final AgentBudgetPolicy budgetPolicy;
    private final AgentBudgetProperties budgetProperties;
    private final OpenAlexQueryFactory queryFactory;
    private final OpenAlexSearchPort openAlexSearchPort;
    private final CandidateDeduplicationService deduplicationService;
    private final CrossrefCandidateLookupService crossrefLookupService;
    private final PaperVerificationService verificationService;
    private final EligiblePaperFilter eligiblePaperFilter;
    private final SearchPlanRefiner planRefiner;
    private final Clock clock;

    public SearchActionExecutor(
            AgentTransitionPolicy transitionPolicy,
            AgentBudgetPolicy budgetPolicy,
            AgentBudgetProperties budgetProperties,
            OpenAlexQueryFactory queryFactory,
            OpenAlexSearchPort openAlexSearchPort,
            CandidateDeduplicationService deduplicationService,
            CrossrefCandidateLookupService crossrefLookupService,
            PaperVerificationService verificationService,
            EligiblePaperFilter eligiblePaperFilter,
            SearchPlanRefiner planRefiner,
            Clock clock
    ) {
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy must not be null");
        this.budgetPolicy = Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
        this.budgetProperties = Objects.requireNonNull(budgetProperties, "budgetProperties must not be null");
        this.queryFactory = Objects.requireNonNull(queryFactory, "queryFactory must not be null");
        this.openAlexSearchPort = Objects.requireNonNull(openAlexSearchPort, "openAlexSearchPort must not be null");
        this.deduplicationService = Objects.requireNonNull(
                deduplicationService, "deduplicationService must not be null");
        this.crossrefLookupService = Objects.requireNonNull(
                crossrefLookupService, "crossrefLookupService must not be null");
        this.verificationService = Objects.requireNonNull(
                verificationService, "verificationService must not be null");
        this.eligiblePaperFilter = Objects.requireNonNull(
                eligiblePaperFilter, "eligiblePaperFilter must not be null");
        this.planRefiner = Objects.requireNonNull(planRefiner, "planRefiner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SearchActionExecutionResult execute(
            AgentExecutionContext context,
            AgentAction action,
            ActionDecisionSource decisionSource
    ) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(action, "action must not be null");
        AgentState initialState = context.state();
        AgentStage stageBefore = initialState.currentStage();
        Instant startedAt = Instant.now(clock);
        BudgetUsageSnapshot budgetBefore = BudgetUsageSnapshot.from(initialState, clock);

        if (!context.planConsistent()) {
            return blocked(context, action, decisionSource, stageBefore, budgetBefore, startedAt,
                    TerminationReason.INVALID_STATE, "trusted plan context does not match AgentState");
        }
        if (!transitionPolicy.isAllowed(initialState, action)) {
            return blocked(context, action, decisionSource, stageBefore, budgetBefore, startedAt,
                    TerminationReason.INVALID_STATE, "action is not allowed for the current stage");
        }

        ActionCost estimate = estimate(context, action);
        BudgetCheckResult check = budgetPolicy.checkBeforeAction(initialState, action, estimate);
        if (!check.allowed()) {
            return blocked(context, action, decisionSource, stageBefore, budgetBefore, startedAt,
                    check.reason(), check.detail());
        }

        ActionExecutionPermit permit = new ActionExecutionPermit(action, estimate);
        AgentState preparedState = initialState.startAction(permit);
        AgentExecutionContext prepared = context.withState(preparedState);
        try {
            return switch (action) {
                case SEARCH_OPENALEX -> search(prepared, decisionSource, stageBefore, budgetBefore, startedAt, permit);
                case DEDUPLICATE_CANDIDATES ->
                        deduplicate(prepared, decisionSource, stageBefore, budgetBefore, startedAt, permit);
                case VERIFY_WITH_CROSSREF ->
                        verify(prepared, decisionSource, stageBefore, budgetBefore, startedAt, permit);
                case EVALUATE_RESULTS ->
                        evaluate(prepared, decisionSource, stageBefore, budgetBefore, startedAt, permit);
                case REFINE_PLAN -> refine(prepared, decisionSource, stageBefore, budgetBefore, startedAt, permit);
                case COMPLETE -> complete(prepared, decisionSource, stageBefore, budgetBefore, startedAt);
                case CREATE_INITIAL_PLAN, TERMINATE ->
                        blocked(prepared, action, decisionSource, stageBefore, budgetBefore, startedAt,
                                TerminationReason.INVALID_STATE, "action is outside the controlled execution loop");
            };
        } catch (RuntimeException exception) {
            TerminationReason reason = isExternalFailure(exception)
                    ? TerminationReason.EXTERNAL_SERVICE_UNAVAILABLE
                    : TerminationReason.UNEXPECTED_FAILURE;
            AgentState failed = prepared.state().terminate(
                    reason, stableFailureCode(exception), Instant.now(clock));
            return result(prepared.withState(failed), action, decisionSource, stageBefore,
                    ExecutionStepStatus.FAILED, "action failed safely", stableFailureCode(exception),
                    ActionCost.none(), budgetBefore, startedAt);
        }
    }

    private SearchActionExecutionResult search(
            AgentExecutionContext context,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt,
            ActionExecutionPermit permit
    ) {
        int remaining = budgetProperties.getMaxUniqueCandidates() - context.state().uniqueCandidateCount();
        OpenAlexQuery query = queryFactory.createBounded(context.state().currentPlan(), remaining);
        int boundedPageSize = query.perPage();
        try {
            OpenAlexSearchResult sourceResult = openAlexSearchPort.search(query);
            List<com.dj1012h.researchpilot.literature.model.CandidatePaper> candidates =
                    sourceResult.candidates().stream().limit(boundedPageSize).toList();
            AgentState next = context.state().recordSearchResult(candidates);
            next = next.recordObservation(new AgentObservation(
                    AgentAction.SEARCH_OPENALEX, stageBefore, AgentStage.CANDIDATES_RETRIEVED, true,
                    candidates.size(), 0, 0, 0, 0, elapsed(startedAt),
                    "OpenAlex search completed", null, Instant.now(clock)), permit);
            return result(context.withState(next), AgentAction.SEARCH_OPENALEX, source, stageBefore,
                    ExecutionStepStatus.SUCCEEDED, "OpenAlex search completed", null,
                    ActionCost.none(), budgetBefore, startedAt);
        } catch (RuntimeException exception) {
            String failureCode = "OPENALEX_SOURCE_UNAVAILABLE";
            AgentState observed = context.state().recordObservation(new AgentObservation(
                    AgentAction.SEARCH_OPENALEX, stageBefore, stageBefore, false,
                    0, 0, 0, 0, 0, elapsed(startedAt),
                    "OpenAlex search failed", failureCode, Instant.now(clock)), permit);
            AgentState terminated = observed.terminate(
                    TerminationReason.EXTERNAL_SERVICE_UNAVAILABLE, failureCode, Instant.now(clock));
            return result(context.withState(terminated), AgentAction.SEARCH_OPENALEX, source, stageBefore,
                    ExecutionStepStatus.FAILED, "OpenAlex source unavailable", failureCode,
                    ActionCost.none(), budgetBefore, startedAt);
        }
    }

    private SearchActionExecutionResult deduplicate(
            AgentExecutionContext context,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt,
            ActionExecutionPermit permit
    ) {
        CandidateDeduplicationResult round = deduplicationService.deduplicate(
                context.state().retrievedCandidates());
        List<NormalizedCandidate> newCandidates = round.uniqueCandidates().stream()
                .filter(candidate -> CandidateDeduplicationKey.from(candidate)
                        .map(key -> !context.state().globalCandidateKeys().contains(key))
                        .orElse(true))
                .toList();
        CandidateDeduplicationResult newOnly = new CandidateDeduplicationResult(
                newCandidates, List.of(), newCandidates.size(), newCandidates.size(), 0);
        int beforeUnique = context.state().uniqueCandidateCount();
        AgentState next = context.state().recordDeduplicatedCandidates(newOnly, permit);
        int added = next.uniqueCandidateCount() - beforeUnique;
        next = next.recordObservation(new AgentObservation(
                AgentAction.DEDUPLICATE_CANDIDATES, stageBefore, AgentStage.CANDIDATES_DEDUPLICATED, true,
                context.state().retrievedCandidates().size(), newCandidates.size(), 0, added, 0,
                elapsed(startedAt), "candidate deduplication completed", null, Instant.now(clock)), permit);
        AgentExecutionContext nextContext = new AgentExecutionContext(
                next, context.validatedPlanContext(), context.lastRefinementResult(), newOnly);
        return result(nextContext, AgentAction.DEDUPLICATE_CANDIDATES, source, stageBefore,
                ExecutionStepStatus.SUCCEEDED, "candidate deduplication completed", null,
                new ActionCost(added, 0), budgetBefore, startedAt);
    }

    private SearchActionExecutionResult verify(
            AgentExecutionContext context,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt,
            ActionExecutionPermit permit
    ) {
        CrossrefLookupSummary lookup = crossrefLookupService.lookup(
                context.currentRoundDeduplication(),
                context.state().requestedCount());
        List<CandidateVerificationOutcome> currentOutcomes = verificationService.verify(lookup);
        List<CandidateVerificationOutcome> allOutcomes = new ArrayList<>(context.state().verificationResults());
        allOutcomes.addAll(currentOutcomes);
        List<SearchResponse.PaperResult> formalPapers =
                eligiblePaperFilter.filter(allOutcomes, context.state().requestedCount());
        AgentState next = context.state().recordVerificationResults(allOutcomes, formalPapers);
        boolean available = lookup.crossrefEnabled() && lookup.sourceAvailable();
        String failureCode = available ? null : "CROSSREF_SOURCE_UNAVAILABLE";
        next = next.recordObservation(new AgentObservation(
                AgentAction.VERIFY_WITH_CROSSREF, stageBefore, AgentStage.VERIFICATION_COMPLETED, available,
                0, context.state().deduplicatedCandidates().size(), formalPapers.size(), 0,
                lookup.attemptedCount(), elapsed(startedAt),
                available ? "Crossref verification completed" : "Crossref source unavailable",
                failureCode, Instant.now(clock)), permit);
        ExecutionStepStatus status = available ? ExecutionStepStatus.SUCCEEDED : ExecutionStepStatus.FAILED;
        if (!available) {
            next = next.terminate(
                    TerminationReason.EXTERNAL_SERVICE_UNAVAILABLE, failureCode, Instant.now(clock));
        }
        return result(context.withState(next), AgentAction.VERIFY_WITH_CROSSREF, source, stageBefore,
                status, available ? "Crossref verification completed" : "Crossref source unavailable",
                failureCode, new ActionCost(0, lookup.attemptedCount()), budgetBefore, startedAt);
    }

    private SearchActionExecutionResult evaluate(
            AgentExecutionContext context,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt,
            ActionExecutionPermit permit
    ) {
        AgentState state = context.state();
        int remaining = Math.max(0, state.requestedCount() - state.verifiedPapers().size());
        boolean canRefine = state.planAdjustmentCount() < budgetProperties.getMaxPlanAdjustments()
                && state.searchRoundCount() < budgetProperties.getMaxSearchRounds();
        String summary = "results evaluated: requested=" + state.requestedCount()
                + ", verified=" + state.verifiedPapers().size()
                + ", remaining=" + remaining
                + ", canRefine=" + canRefine;
        AgentState next = state.recordObservation(new AgentObservation(
                AgentAction.EVALUATE_RESULTS, stageBefore, AgentStage.EVALUATING_RESULTS, true,
                state.retrievedCandidates().size(), state.deduplicatedCandidates().size(),
                state.verifiedPapers().size(), 0, 0, elapsed(startedAt),
                summary, null, Instant.now(clock)), permit);
        return result(context.withState(next), AgentAction.EVALUATE_RESULTS, source, stageBefore,
                ExecutionStepStatus.SUCCEEDED, summary, null, ActionCost.none(), budgetBefore, startedAt);
    }

    private SearchActionExecutionResult refine(
            AgentExecutionContext context,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt,
            ActionExecutionPermit permit
    ) {
        AgentState state = context.state();
        SearchPlanRefinementResult refinement = planRefiner.refine(new SearchPlanRefinementContext(
                context.validatedPlanContext(),
                state.planAdjustmentCount() - 1,
                state.retrievedCandidates().size(),
                state.verifiedPapers().size(),
                "INSUFFICIENT_VERIFIED_RESULTS"
        ));
        ValidatedSearchPlanContext validated = new ValidatedSearchPlanContext(
                context.validatedPlanContext().generationContext(),
                new SearchPlanValidationResult(refinement.refinedPlan(), refinement.origins()));
        AgentState next = state.recordRefinedPlan(refinement.refinedPlan(), permit);
        next = next.recordObservation(new AgentObservation(
                AgentAction.REFINE_PLAN, stageBefore, AgentStage.PLAN_READY, true,
                0, 0, state.verifiedPapers().size(), 0, 0, elapsed(startedAt),
                "search plan refined and revalidated", null, Instant.now(clock)), permit);
        AgentExecutionContext nextContext = new AgentExecutionContext(
                next, validated, refinement, CandidateDeduplicationResult.empty());
        return result(nextContext, AgentAction.REFINE_PLAN, source, stageBefore,
                ExecutionStepStatus.SUCCEEDED, "search plan refined and revalidated", null,
                ActionCost.none(), budgetBefore, startedAt);
    }

    private SearchActionExecutionResult complete(
            AgentExecutionContext context,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt
    ) {
        int verified = context.state().verifiedPapers().size();
        TerminationReason reason = verified >= context.state().requestedCount()
                ? TerminationReason.TARGET_REACHED
                : verified > 0 ? TerminationReason.PARTIAL_RESULTS : TerminationReason.NO_VERIFIED_RESULTS;
        AgentState completed = context.state().complete(reason, "controlled execution completed", Instant.now(clock));
        return result(context.withState(completed), AgentAction.COMPLETE, source, stageBefore,
                ExecutionStepStatus.SUCCEEDED, "controlled execution completed", null,
                ActionCost.none(), budgetBefore, startedAt);
    }

    private ActionCost estimate(AgentExecutionContext context, AgentAction action) {
        AgentState state = context.state();
        return switch (action) {
            case SEARCH_OPENALEX -> {
                int requested = queryFactory.create(state.currentPlan()).perPage();
                int remaining = budgetProperties.getMaxUniqueCandidates() - state.uniqueCandidateCount();
                yield new ActionCost(remaining > 0 ? Math.min(requested, remaining) : 1, 0);
            }
            case DEDUPLICATE_CANDIDATES -> new ActionCost(state.retrievedCandidates().size(), 0);
            case VERIFY_WITH_CROSSREF -> new ActionCost(0, state.deduplicatedCandidates().size());
            default -> ActionCost.none();
        };
    }

    private SearchActionExecutionResult blocked(
            AgentExecutionContext context,
            AgentAction action,
            ActionDecisionSource source,
            AgentStage stageBefore,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt,
            TerminationReason reason,
            String detail
    ) {
        AgentState state = context.state().terminate(reason, detail, Instant.now(clock));
        return result(context.withState(state), action, source, stageBefore, ExecutionStepStatus.BLOCKED,
                detail, reason.name(), ActionCost.none(), budgetBefore, startedAt);
    }

    private SearchActionExecutionResult result(
            AgentExecutionContext context,
            AgentAction action,
            ActionDecisionSource source,
            AgentStage stageBefore,
            ExecutionStepStatus status,
            String summary,
            String failureCode,
            ActionCost actualCost,
            BudgetUsageSnapshot budgetBefore,
            Instant startedAt
    ) {
        Instant finishedAt = Instant.now(clock);
        return new SearchActionExecutionResult(
                context, action, source, stageBefore, context.state().currentStage(), status,
                summary, failureCode, actualCost, budgetBefore,
                BudgetUsageSnapshot.from(context.state(), clock), startedAt, finishedAt);
    }

    private Duration elapsed(Instant startedAt) {
        Duration elapsed = Duration.between(startedAt, Instant.now(clock));
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    private String stableFailureCode(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank() ? "UNEXPECTED_FAILURE" : simpleName;
    }

    private boolean isExternalFailure(RuntimeException exception) {
        return exception instanceof CrossrefApiException
                || exception instanceof ModelInvocationException
                || exception instanceof ModelNotConfiguredException;
    }
}
