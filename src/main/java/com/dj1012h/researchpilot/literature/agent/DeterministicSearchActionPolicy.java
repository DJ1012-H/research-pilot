package com.dj1012h.researchpilot.literature.agent;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** Safe fallback: it never invents or expands an executable action. */
@Component
public class DeterministicSearchActionPolicy {

    public AgentAction choose(AgentState state, Set<AgentAction> allowedActions) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(allowedActions, "allowedActions must not be null");
        if (allowedActions.size() == 1) return allowedActions.iterator().next();
        if (state.verifiedPapers().size() >= state.requestedCount() && allowedActions.contains(AgentAction.COMPLETE)) {
            return AgentAction.COMPLETE;
        }
        if (!state.retrievedCandidates().isEmpty() && allowedActions.contains(AgentAction.DEDUPLICATE_CANDIDATES)) {
            return AgentAction.DEDUPLICATE_CANDIDATES;
        }
        if (!state.deduplicatedCandidates().isEmpty() && allowedActions.contains(AgentAction.VERIFY_WITH_CROSSREF)) {
            return AgentAction.VERIFY_WITH_CROSSREF;
        }
        if (allowedActions.contains(AgentAction.EVALUATE_RESULTS)) return AgentAction.EVALUATE_RESULTS;
        if (allowedActions.contains(AgentAction.REFINE_PLAN)) return AgentAction.REFINE_PLAN;
        if (allowedActions.contains(AgentAction.COMPLETE)) return AgentAction.COMPLETE;
        throw new IllegalArgumentException("fallback requires at least one executable action");
    }
}
