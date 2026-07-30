package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Adds durable writes to the existing in-memory trace semantics when enabled. */
@Component
@Primary
@ConditionalOnProperty(name = "app.literature.persistence.enabled", havingValue = "true")
public class PersistingExecutionTraceRecorder implements ExecutionTraceRecorder {

    private final InMemoryExecutionTraceRecorder inMemory;
    private final LiteraturePersistenceFacade persistence;

    public PersistingExecutionTraceRecorder(
            InMemoryExecutionTraceRecorder inMemory,
            LiteraturePersistenceFacade persistence
    ) {
        this.inMemory = Objects.requireNonNull(inMemory, "inMemory must not be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence must not be null");
    }

    @Override
    public ExecutionTraceEntry record(UUID traceId, ExecutionTraceDraft draft) {
        ExecutionTraceEntry entry = inMemory.record(traceId, draft);
        persistence.appendExecutionStep(traceId, entry);
        return entry;
    }

    @Override
    public List<ExecutionTraceEntry> entries(UUID traceId) {
        return inMemory.entries(traceId);
    }
}
