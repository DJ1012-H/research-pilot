package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;

import java.util.Objects;

/** Result of the controlled OpenAlex boundary; result is absent when no call was permitted. */
public record ControlledOpenAlexSearchResult(AgentState state, OpenAlexSearchResult result) {
    public ControlledOpenAlexSearchResult {
        state = Objects.requireNonNull(state, "state must not be null");
    }
    public boolean executed() { return result != null; }
}
