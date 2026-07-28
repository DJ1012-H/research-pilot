package com.dj1012h.researchpilot.literature.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Structural state-machine whitelist; budget and tool concerns stay elsewhere. */
@Component
public class AgentTransitionPolicy {

    public Set<AgentAction> allowedActions(AgentState state) {
        if (state == null || state.terminated()) {
            return Set.of();
        }
        return switch (state.currentStage()) {
            case INITIALIZED -> Set.of(AgentAction.CREATE_INITIAL_PLAN);
            case PLAN_READY -> Set.of(AgentAction.SEARCH_OPENALEX);
            case SEARCHING, DEDUPLICATING, VERIFYING, COMPLETED, TERMINATED -> Set.of();
            case CANDIDATES_RETRIEVED -> Set.of(AgentAction.DEDUPLICATE_CANDIDATES);
            case CANDIDATES_DEDUPLICATED -> state.deduplicatedCandidates().isEmpty()
                    ? Set.of(AgentAction.EVALUATE_RESULTS)
                    : Set.of(AgentAction.VERIFY_WITH_CROSSREF);
            case VERIFICATION_COMPLETED -> Set.of(AgentAction.EVALUATE_RESULTS);
            case EVALUATING_RESULTS -> state.verifiedPapers().size() >= state.requestedCount()
                    ? Set.of(AgentAction.COMPLETE)
                    : Set.of(AgentAction.REFINE_PLAN, AgentAction.COMPLETE);
        };
    }

    public boolean isAllowed(AgentState state, AgentAction action) {
        return action != null && allowedActions(state).contains(action);
    }

    /** Java-owned termination remains executable in every active state. */
    public boolean canExecute(AgentState state, AgentAction action) {
        if (state == null || action == null || state.terminated()) {
            return false;
        }
        return action == AgentAction.TERMINATE || isAllowed(state, action);
    }
}
