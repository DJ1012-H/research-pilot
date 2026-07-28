package com.dj1012h.researchpilot.literature.agent;

/** Explicit actions only; no model-selected action contract exists at this stage. */
public enum AgentAction {
    CREATE_INITIAL_PLAN(AgentActionType.INTERNAL, true),
    SEARCH_OPENALEX(AgentActionType.OPENALEX_SEARCH, true),
    DEDUPLICATE_CANDIDATES(AgentActionType.INTERNAL, true),
    VERIFY_WITH_CROSSREF(AgentActionType.CROSSREF_VERIFICATION, true),
    EVALUATE_RESULTS(AgentActionType.INTERNAL, true),
    REFINE_PLAN(AgentActionType.PLAN_ADJUSTMENT, true),
    COMPLETE(AgentActionType.TERMINAL, false),
    TERMINATE(AgentActionType.TERMINAL, false);

    private final AgentActionType type;
    private final boolean businessStep;

    AgentAction(AgentActionType type, boolean businessStep) {
        this.type = type;
        this.businessStep = businessStep;
    }

    public AgentActionType type() { return type; }
    public boolean countsAsBusinessStep() { return businessStep; }
}
