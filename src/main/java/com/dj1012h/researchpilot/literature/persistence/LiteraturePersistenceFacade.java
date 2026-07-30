package com.dj1012h.researchpilot.literature.persistence;

import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.ExecutionTraceEntry;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;

import java.time.Instant;
import java.util.UUID;

/**
 * Bounded persistence use cases for the literature runtime. Domain state stays
 * in Java; this port only stores an audit projection.
 */
public interface LiteraturePersistenceFacade {
    void createRunningTask(UUID taskId, SearchRequest request, int requestedCount, Instant startedAt);

    void appendExecutionStep(UUID taskId, ExecutionTraceEntry entry);

    void finalizeSuccess(UUID taskId, AgentRunResult runResult, ReviewOutcome reviewOutcome, Instant completedAt);

    void finalizeFailure(UUID taskId, String failureCode, Instant completedAt);
}
