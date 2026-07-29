package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled finite workflow. Existing single-action entry points remain for
 * compatibility; {@link #execute(AgentState, ValidatedSearchPlanContext)}
 * orchestrates the internal two-round execution path.
 */
@Component
public class LiteratureResearchAgent {

    private final AgentBudgetPolicy budgetPolicy;
    private final AgentBudgetProperties budgetProperties;
    private final LiteratureSearchProperties searchProperties;
    private final OpenAlexSearchPort openAlexSearchPort;
    private final AgentTransitionPolicy transitionPolicy;
    private final SearchActionDecider actionDecider;
    private final SearchActionExecutor actionExecutor;
    private final ExecutionTraceRecorder traceRecorder;
    private final Clock clock;

    public LiteratureResearchAgent(
            AgentBudgetPolicy budgetPolicy,
            AgentBudgetProperties budgetProperties,
            LiteratureSearchProperties searchProperties,
            OpenAlexSearchPort openAlexSearchPort,
            AgentTransitionPolicy transitionPolicy,
            SearchActionDecider actionDecider,
            SearchActionExecutor actionExecutor,
            ExecutionTraceRecorder traceRecorder,
            Clock clock
    ) {
        this.budgetPolicy = Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
        this.budgetProperties = Objects.requireNonNull(budgetProperties, "budgetProperties must not be null");
        this.searchProperties = Objects.requireNonNull(searchProperties, "searchProperties must not be null");
        this.openAlexSearchPort = Objects.requireNonNull(openAlexSearchPort, "openAlexSearchPort must not be null");
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy must not be null");
        this.actionDecider = Objects.requireNonNull(actionDecider, "actionDecider must not be null");
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor must not be null");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AgentState initialize(SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int requestedCount = request.limit() == null ? searchProperties.getDefaultResultLimit() : request.limit();
        return AgentState.initialize(request.query(), requestedCount, clock, budgetProperties.getTotalTimeout());
    }

    public AgentState registerInitialPlan(AgentState state, SearchPlan plan) {
        Objects.requireNonNull(state, "state must not be null");
        BudgetCheckResult check = budgetPolicy.checkBeforeAction(state, AgentAction.CREATE_INITIAL_PLAN, ActionCost.none());
        if (!check.allowed()) return state.terminate(check.reason(), check.detail(), Instant.now(clock));
        return state.startAction(new ActionExecutionPermit(AgentAction.CREATE_INITIAL_PLAN, ActionCost.none()))
                .recordInitialPlan(plan);
    }

    public ActionPreparation prepareAction(AgentState state, AgentAction action, ActionCost estimatedCost) {
        BudgetCheckResult check = budgetPolicy.checkBeforeAction(state, action, estimatedCost);
        if (!check.allowed()) {
            return new ActionPreparation(state.terminate(check.reason(), check.detail(), Instant.now(clock)), check, null);
        }
        ActionExecutionPermit permit = new ActionExecutionPermit(action, estimatedCost);
        return new ActionPreparation(state.startAction(permit), check, permit);
    }

    /**
     * A single guarded tool boundary used to prove the pre-call policy. The
     * returned candidates are bounded by the checked page-size estimate.
     */
    public ControlledOpenAlexSearchResult executeOpenAlexSearch(AgentState state, OpenAlexQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        ActionCost cost = new ActionCost(query.perPage() == null ? OpenAlexQuery.MAX_PAGE_SIZE : query.perPage(), 0);
        AgentStage before = Objects.requireNonNull(state, "state must not be null").currentStage();
        ActionPreparation preparation = prepareAction(state, AgentAction.SEARCH_OPENALEX, cost);
        if (!preparation.checkResult().allowed()) return new ControlledOpenAlexSearchResult(preparation.state(), null);

        AgentState preparedState = preparation.state();
        Instant startedAt = Instant.now(clock);
        try {
            OpenAlexSearchResult sourceResult = openAlexSearchPort.search(query);
            List<com.dj1012h.researchpilot.literature.model.CandidatePaper> bounded = sourceResult.candidates().stream()
                    .limit(cost.uniqueCandidates())
                    .toList();
            OpenAlexSearchResult result = new OpenAlexSearchResult(sourceResult.totalMatches(), bounded, sourceResult.nextCursor());
            AgentState next = preparedState.recordSearchResult(bounded).recordObservation(new AgentObservation(
                    AgentAction.SEARCH_OPENALEX, before, AgentStage.CANDIDATES_RETRIEVED, true,
                    bounded.size(), 0, 0, 0, 0, elapsed(startedAt), "OpenAlex search completed", null,
                    Instant.now(clock)), preparation.permit());
            return new ControlledOpenAlexSearchResult(next, result);
        } catch (RuntimeException exception) {
            AgentState next = preparedState.recordObservation(new AgentObservation(
                    AgentAction.SEARCH_OPENALEX, before, before, false, 0, 0, 0, 0, 0,
                    elapsed(startedAt), "OpenAlex search failed", "OPENALEX_FAILURE", Instant.now(clock)),
                    preparation.permit());
            return new ControlledOpenAlexSearchResult(next, null);
        }
    }

    public AgentState recordObservation(AgentState state, AgentObservation observation, ActionExecutionPermit permit) {
        return state.recordObservation(observation, permit);
    }

    public AgentState terminate(AgentState state, TerminationReason reason, String detail) {
        return state.terminate(reason, detail, Instant.now(clock));
    }

    public AgentState complete(AgentState state, TerminationReason reason, String detail) {
        return state.complete(reason, detail, Instant.now(clock));
    }

    /**
     * Runs only the internal Agent path; controllers and the public search API
     * deliberately remain disconnected in this delivery.
     */
    public AgentRunResult execute(
            AgentState initializedState,
            ValidatedSearchPlanContext initialPlanContext
    ) {
        Objects.requireNonNull(initializedState, "initializedState must not be null");
        Objects.requireNonNull(initialPlanContext, "initialPlanContext must not be null");
        UUID traceId = UUID.randomUUID();
        if (!initializedState.terminated()
                && !Instant.now(clock).isBefore(initializedState.deadline())) {
            AgentExecutionContext expired = terminateAndTrace(
                    traceId,
                    AgentExecutionContext.initial(initializedState, initialPlanContext),
                    TerminationReason.DEADLINE_EXCEEDED,
                    "deadline reached"
            );
            return new AgentRunResult(traceId, expired, traceRecorder.entries(traceId));
        }
        AgentState state = initializedState.currentStage() == AgentStage.INITIALIZED
                ? registerInitialPlan(initializedState, initialPlanContext.validationResult().plan())
                : initializedState;
        AgentExecutionContext context = AgentExecutionContext.initial(state, initialPlanContext);

        int maximumAttempts = budgetProperties.getMaxBusinessSteps() + 1;
        for (int attempt = 0; attempt < maximumAttempts && !context.state().terminated(); attempt++) {
            if (!Instant.now(clock).isBefore(context.state().deadline())) {
                context = terminateAndTrace(
                        traceId, context, TerminationReason.DEADLINE_EXCEEDED, "deadline reached");
                break;
            }
            if (!context.planConsistent()) {
                context = terminateAndTrace(
                        traceId, context, TerminationReason.INVALID_STATE,
                        "trusted plan context does not match AgentState");
                break;
            }

            Set<AgentAction> allowed = transitionPolicy.allowedActions(context.state());
            if (allowed.isEmpty()) {
                context = terminateAndTrace(
                        traceId, context, TerminationReason.INVALID_STATE,
                        "no allowed action for active stage " + context.state().currentStage());
                break;
            }

            SearchActionDecision decision;
            try {
                if (allowed.size() == 1) {
                    decision = new SearchActionDecision(
                            allowed.iterator().next(), ActionDecisionSource.POLICY_SINGLE_ACTION);
                } else {
                    if (context.state().currentStage() != AgentStage.EVALUATING_RESULTS
                            || !allowed.equals(Set.of(AgentAction.REFINE_PLAN, AgentAction.COMPLETE))) {
                        context = terminateAndTrace(
                                traceId, context, TerminationReason.INVALID_STATE,
                                "multiple actions are only valid while evaluating results");
                        break;
                    }
                    decision = actionDecider.decide(context.state());
                }
            } catch (SearchActionDecisionUnavailableException exception) {
                context = terminateAndTrace(
                        traceId, context, exception.getTerminationReason(), exception.getMessage());
                break;
            } catch (RuntimeException exception) {
                context = terminateAndTrace(
                        traceId, context, TerminationReason.UNEXPECTED_FAILURE,
                        stableFailureCode(exception));
                break;
            }

            if (!transitionPolicy.isAllowed(context.state(), decision.action())) {
                context = terminateAndTrace(
                        traceId, context, TerminationReason.INVALID_STATE,
                        "selected action is no longer allowed");
                break;
            }

            SearchActionExecutionResult execution =
                    actionExecutor.execute(context, decision.action(), decision.source());
            traceRecorder.record(traceId, execution.traceDraft());
            context = execution.context();
        }

        if (!context.state().terminated()) {
            context = terminateAndTrace(
                    traceId, context, TerminationReason.STEP_LIMIT_REACHED,
                    "defensive execution bound reached");
        }
        return new AgentRunResult(traceId, context, traceRecorder.entries(traceId));
    }

    private AgentExecutionContext terminateAndTrace(
            UUID traceId,
            AgentExecutionContext context,
            TerminationReason reason,
            String detail
    ) {
        AgentState before = context.state();
        Instant at = Instant.now(clock);
        AgentState terminated = before.terminate(reason, safeDetail(detail), at);
        AgentExecutionContext next = context.withState(terminated);
        BudgetUsageSnapshot budgetBefore = BudgetUsageSnapshot.from(before, clock);
        traceRecorder.record(traceId, new ExecutionTraceDraft(
                AgentAction.TERMINATE,
                null,
                before.currentStage(),
                terminated.currentStage(),
                ExecutionStepStatus.BLOCKED,
                0,
                budgetBefore,
                BudgetUsageSnapshot.from(terminated, clock),
                safeDetail(detail),
                reason.name(),
                reason,
                at,
                at
        ));
        return next;
    }

    private String safeDetail(String value) {
        String normalized = value == null || value.isBlank()
                ? "controlled execution terminated"
                : value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String stableFailureCode(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank() ? "UNEXPECTED_FAILURE" : simpleName;
    }

    private Duration elapsed(Instant startedAt) {
        Duration elapsed = Duration.between(startedAt, Instant.now(clock));
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}
