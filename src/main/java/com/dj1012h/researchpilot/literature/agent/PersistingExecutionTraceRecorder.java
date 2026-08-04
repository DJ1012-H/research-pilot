package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Adds durable writes to the existing in-memory trace semantics when enabled. */
@Component
@Primary
@ConditionalOnProperty(name = "app.literature.persistence.enabled", havingValue = "true")
public class PersistingExecutionTraceRecorder implements ExecutionTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(PersistingExecutionTraceRecorder.class);
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
        long startedAt = System.nanoTime();
        try {
            persistence.appendExecutionStep(traceId, entry);
            log.info(
                    "event=literature_persistence operation=APPEND_EXECUTION_STEP "
                            + "outcome=SUCCEEDED durationMs={}",
                    elapsedMillis(startedAt)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event=literature_persistence operation=APPEND_EXECUTION_STEP "
                            + "outcome=FAILED durationMs={} exceptionType={}",
                    elapsedMillis(startedAt), exception.getClass().getSimpleName()
            );
            throw exception;
        }
        return entry;
    }

    @Override
    public List<ExecutionTraceEntry> entries(UUID traceId) {
        return inMemory.entries(traceId);
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }
}
