package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Conservative, read-only cost estimates used only to filter a model's choices. */
@Component
public class SearchActionCostEstimator {

    private final AgentBudgetProperties budgetProperties;

    public SearchActionCostEstimator(AgentBudgetProperties budgetProperties) {
        this.budgetProperties = Objects.requireNonNull(budgetProperties, "budgetProperties must not be null");
    }

    public ActionCost estimate(AgentState state, AgentAction action) {
        Objects.requireNonNull(state, "state must not be null");
        return switch (Objects.requireNonNull(action, "action must not be null")) {
            case SEARCH_OPENALEX -> new ActionCost(budgetProperties.getMaxUniqueCandidates(), 0);
            case DEDUPLICATE_CANDIDATES -> new ActionCost(state.retrievedCandidates().size(), 0);
            case VERIFY_WITH_CROSSREF -> new ActionCost(0, state.deduplicatedCandidates().size());
            default -> ActionCost.none();
        };
    }
}
