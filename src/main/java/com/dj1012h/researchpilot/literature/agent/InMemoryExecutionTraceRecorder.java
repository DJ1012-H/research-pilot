package com.dj1012h.researchpilot.literature.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Concurrent, in-memory trace isolation for one application process. */
@Component
public class InMemoryExecutionTraceRecorder implements ExecutionTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(InMemoryExecutionTraceRecorder.class);
    private final ConcurrentMap<UUID, List<ExecutionTraceEntry>> traces = new ConcurrentHashMap<>();

    @Override
    public ExecutionTraceEntry record(UUID traceId, ExecutionTraceDraft draft) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(draft, "draft must not be null");
        List<ExecutionTraceEntry> entries = traces.computeIfAbsent(traceId, ignored -> new ArrayList<>());
        synchronized (entries) {
            validateSequence(entries, draft);
            ExecutionTraceEntry entry = new ExecutionTraceEntry(
                    traceId,
                    entries.size(),
                    draft.action(),
                    draft.decisionSource(),
                    draft.stageBefore(),
                    draft.stageAfter(),
                    draft.status(),
                    draft.elapsedMs(),
                    draft.budgetBefore(),
                    draft.budgetAfter(),
                    draft.observationSummary(),
                    draft.failureCode(),
                    draft.terminationReason(),
                    draft.startedAt(),
                    draft.finishedAt()
            );
            entries.add(entry);
            log.info(
                    "event=literature_agent_step action={} status={} durationMs={}",
                    entry.action(), entry.status(), entry.elapsedMs()
            );
            return entry;
        }
    }

    @Override
    public List<ExecutionTraceEntry> entries(UUID traceId) {
        List<ExecutionTraceEntry> entries = traces.get(Objects.requireNonNull(traceId, "traceId must not be null"));
        if (entries == null) return List.of();
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    private void validateSequence(List<ExecutionTraceEntry> entries, ExecutionTraceDraft draft) {
        assertMonotonic(draft.budgetBefore(), draft.budgetAfter());
        if (entries.isEmpty()) return;
        ExecutionTraceEntry previous = entries.getLast();
        if (previous.terminationReason() == null
                && previous.stageAfter() != draft.stageBefore()) {
            throw new IllegalArgumentException("trace stages must form a continuous sequence");
        }
        assertMonotonic(previous.budgetAfter(), draft.budgetBefore());
    }

    private void assertMonotonic(BudgetUsageSnapshot before, BudgetUsageSnapshot after) {
        if (after.searchRoundCount() < before.searchRoundCount()
                || after.planAdjustmentCount() < before.planAdjustmentCount()
                || after.businessStepCount() < before.businessStepCount()
                || after.uniqueCandidateCount() < before.uniqueCandidateCount()
                || after.crossrefCallCount() < before.crossrefCallCount()
                || (before.deadlineExceeded() && !after.deadlineExceeded())) {
            throw new IllegalArgumentException("trace budget usage must be monotonic");
        }
    }
}
