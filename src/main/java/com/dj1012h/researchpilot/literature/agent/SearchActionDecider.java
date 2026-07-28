package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only decision boundary. It filters Java-owned policy and budgets before
 * one optional model proposal, and never starts actions or calls external tools.
 */
@Component
public class SearchActionDecider {

    private final AgentTransitionPolicy transitionPolicy;
    private final AgentBudgetPolicy budgetPolicy;
    private final SearchActionCostEstimator costEstimator;
    private final SearchActionGenerator generator;
    private final SearchActionValidationPipeline validationPipeline;
    private final SearchActionContextBuilder contextBuilder;
    private final DeterministicSearchActionPolicy fallbackPolicy;

    public SearchActionDecider(AgentTransitionPolicy transitionPolicy,
                               AgentBudgetPolicy budgetPolicy,
                               SearchActionCostEstimator costEstimator,
                               SearchActionGenerator generator,
                               SearchActionValidationPipeline validationPipeline,
                               SearchActionContextBuilder contextBuilder,
                               DeterministicSearchActionPolicy fallbackPolicy) {
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy must not be null");
        this.budgetPolicy = Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
        this.costEstimator = Objects.requireNonNull(costEstimator, "costEstimator must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.validationPipeline = Objects.requireNonNull(validationPipeline, "validationPipeline must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy must not be null");
    }

    public SearchActionDecision decide(AgentState state) {
        Objects.requireNonNull(state, "state must not be null");
        if (state.terminated()) throw new IllegalStateException("terminated state cannot be decided again");

        Set<AgentAction> executableActions = new LinkedHashSet<>();
        BudgetCheckResult firstDenied = null;
        for (AgentAction action : transitionPolicy.allowedActions(state)) {
            BudgetCheckResult check = budgetPolicy.checkBeforeAction(state, action, costEstimator.estimate(state, action));
            if (check.allowed()) executableActions.add(action);
            else if (firstDenied == null) firstDenied = check;
        }
        if (executableActions.isEmpty()) {
            if (firstDenied != null) {
                throw new SearchActionDecisionUnavailableException(firstDenied.reason(), firstDenied.detail());
            }
            throw new SearchActionDecisionUnavailableException(TerminationReason.INVALID_STATE,
                    "no structurally executable action for stage " + state.currentStage());
        }
        Set<AgentAction> frozenActions = Set.copyOf(executableActions);
        if (frozenActions.size() == 1) {
            return new SearchActionDecision(frozenActions.iterator().next(), ActionDecisionSource.POLICY_SINGLE_ACTION);
        }

        try {
            SearchActionContext context = contextBuilder.build(state, frozenActions);
            AgentAction action = validationPipeline.validate(generator.generate(context), frozenActions);
            return new SearchActionDecision(action, ActionDecisionSource.MODEL);
        } catch (ModelNotConfiguredException | ModelInvocationException | SearchActionValidationException exception) {
            return new SearchActionDecision(fallbackPolicy.choose(state, frozenActions),
                    ActionDecisionSource.DETERMINISTIC_FALLBACK);
        }
    }
}
