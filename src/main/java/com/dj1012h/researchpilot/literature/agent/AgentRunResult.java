package com.dj1012h.researchpilot.literature.agent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal result of one finite controlled execution. */
public record AgentRunResult(
        UUID traceId,
        AgentExecutionContext context,
        List<ExecutionTraceEntry> trace
) {
    public AgentRunResult {
        traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        context = Objects.requireNonNull(context, "context must not be null");
        trace = List.copyOf(Objects.requireNonNull(trace, "trace must not be null"));
    }

    /**
     * The single authoritative final state for response assembly. Trace data
     * remains diagnostic-only and is deliberately not exposed by the API.
     */
    public AgentState finalState() {
        return context.state();
    }
}
