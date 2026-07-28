package com.dj1012h.researchpilot.literature.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchActionBusinessValidator {

    private static final Set<AgentAction> MODEL_SELECTABLE_ACTIONS = Set.of(
            AgentAction.SEARCH_OPENALEX,
            AgentAction.DEDUPLICATE_CANDIDATES,
            AgentAction.VERIFY_WITH_CROSSREF,
            AgentAction.EVALUATE_RESULTS,
            AgentAction.REFINE_PLAN,
            AgentAction.COMPLETE
    );

    public AgentAction validate(SearchActionDraft draft) {
        if (draft == null || draft.action() == null || draft.action().isBlank()) {
            throw failure("ACTION_REQUIRED");
        }
        final AgentAction action;
        try {
            action = AgentAction.valueOf(draft.action());
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_ACTION");
        }
        if (!MODEL_SELECTABLE_ACTIONS.contains(action)) {
            throw failure("ACTION_NOT_MODEL_SELECTABLE");
        }
        return action;
    }

    public Set<AgentAction> modelSelectableActions() {
        return MODEL_SELECTABLE_ACTIONS;
    }

    private SearchActionValidationException failure(String code) {
        return new SearchActionValidationException(SearchActionValidationStage.BUSINESS_RULE,
                List.of(new SearchActionValidationIssue(code, "$.action", true)));
    }
}
