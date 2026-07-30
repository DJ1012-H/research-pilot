package com.dj1012h.researchpilot.literature.persistence;

import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.ExecutionTraceEntry;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Default disabled-mode implementation; it deliberately performs no database work. */
@Component
@ConditionalOnProperty(name = "app.literature.persistence.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpLiteraturePersistenceFacade implements LiteraturePersistenceFacade {

    public static final NoOpLiteraturePersistenceFacade INSTANCE = new NoOpLiteraturePersistenceFacade();

    @Override public void createRunningTask(UUID taskId, SearchRequest request, int requestedCount, Instant startedAt) { }
    @Override public void appendExecutionStep(UUID taskId, ExecutionTraceEntry entry) { }
    @Override public void finalizeSuccess(UUID taskId, AgentRunResult runResult, ReviewOutcome reviewOutcome, Instant completedAt) { }
    @Override public void finalizeFailure(UUID taskId, String failureCode, Instant completedAt) { }
}
