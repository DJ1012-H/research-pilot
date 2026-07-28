package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;

/** Trusted action selected only after policy, budget, and output validation. */
public record SearchActionDecision(AgentAction action, ActionDecisionSource source) {
    public SearchActionDecision {
        action = Objects.requireNonNull(action, "action must not be null");
        source = Objects.requireNonNull(source, "source must not be null");
    }
}
