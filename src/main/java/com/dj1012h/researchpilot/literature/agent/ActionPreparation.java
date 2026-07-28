package com.dj1012h.researchpilot.literature.agent;

import java.util.Objects;

/** Result of preparing one explicit action, including a terminated state on denial. */
public record ActionPreparation(AgentState state, BudgetCheckResult checkResult, ActionExecutionPermit permit) {
    public ActionPreparation {
        state = Objects.requireNonNull(state, "state must not be null");
        checkResult = Objects.requireNonNull(checkResult, "checkResult must not be null");
        if (checkResult.allowed() != (permit != null)) {
            throw new IllegalArgumentException("only an allowed action may have a permit");
        }
    }
}
