package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;

/** Opaque evidence that one specific action passed the pre-call budget check. */
public record ActionExecutionPermit(AgentAction action, ActionCost estimatedCost) {
    public ActionExecutionPermit {
        action = Objects.requireNonNull(action, "action must not be null");
        estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
    }
}
