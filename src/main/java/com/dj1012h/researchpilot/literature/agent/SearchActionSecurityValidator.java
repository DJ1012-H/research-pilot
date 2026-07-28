package com.dj1012h.researchpilot.literature.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class SearchActionSecurityValidator {

    public AgentAction validate(AgentAction candidateAction, Set<AgentAction> allowedActions) {
        Objects.requireNonNull(candidateAction, "candidateAction must not be null");
        Objects.requireNonNull(allowedActions, "allowedActions must not be null");
        if (!allowedActions.contains(candidateAction)) {
            throw new SearchActionValidationException(SearchActionValidationStage.SECURITY,
                    List.of(new SearchActionValidationIssue("ACTION_NOT_ALLOWED", "$.action", false)));
        }
        return candidateAction;
    }
}
