package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Controlled workflow skeleton. It orchestrates explicit state updates and a
 * guarded OpenAlex boundary only; it deliberately contains no autonomous loop
 * or model-selected action policy.
 */
@Component
public class LiteratureResearchAgent {

    private final AgentBudgetPolicy budgetPolicy;
    private final AgentBudgetProperties budgetProperties;
    private final LiteratureSearchProperties searchProperties;
    private final OpenAlexSearchPort openAlexSearchPort;
    private final Clock clock;

    public LiteratureResearchAgent(
            AgentBudgetPolicy budgetPolicy,
            AgentBudgetProperties budgetProperties,
            LiteratureSearchProperties searchProperties,
            OpenAlexSearchPort openAlexSearchPort,
            Clock clock
    ) {
        this.budgetPolicy = Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
        this.budgetProperties = Objects.requireNonNull(budgetProperties, "budgetProperties must not be null");
        this.searchProperties = Objects.requireNonNull(searchProperties, "searchProperties must not be null");
        this.openAlexSearchPort = Objects.requireNonNull(openAlexSearchPort, "openAlexSearchPort must not be null");
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

    private Duration elapsed(Instant startedAt) {
        Duration elapsed = Duration.between(startedAt, Instant.now(clock));
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}
