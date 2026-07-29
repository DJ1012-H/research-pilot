package com.dj1012h.researchpilot.literature.agent;

import java.util.List;
import java.util.UUID;

public interface ExecutionTraceRecorder {
    ExecutionTraceEntry record(UUID traceId, ExecutionTraceDraft draft);
    List<ExecutionTraceEntry> entries(UUID traceId);
}
