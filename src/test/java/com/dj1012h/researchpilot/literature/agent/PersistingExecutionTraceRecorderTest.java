package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class PersistingExecutionTraceRecorderTest {

    @Test
    void shouldMeasureStepPersistenceWithoutLoggingTraceData(CapturedOutput output) {
        LiteraturePersistenceFacade persistence = mock(LiteraturePersistenceFacade.class);
        PersistingExecutionTraceRecorder recorder = new PersistingExecutionTraceRecorder(
                new InMemoryExecutionTraceRecorder(),
                persistence
        );
        UUID traceId = UUID.randomUUID();
        String privateSummary = "private-summary-must-not-appear";

        ExecutionTraceEntry entry = recorder.record(traceId, draft(privateSummary));

        verify(persistence).appendExecutionStep(traceId, entry);
        assertThat(output)
                .contains("event=literature_persistence")
                .contains("operation=APPEND_EXECUTION_STEP")
                .contains("outcome=SUCCEEDED")
                .containsPattern("durationMs=\\d+")
                .doesNotContain(traceId.toString())
                .doesNotContain(privateSummary);
    }

    private ExecutionTraceDraft draft(String summary) {
        Instant at = Instant.parse("2026-08-04T00:00:00Z");
        BudgetUsageSnapshot budget = new BudgetUsageSnapshot(0, 0, 0, 0, 0, false);
        return new ExecutionTraceDraft(
                AgentAction.SEARCH_OPENALEX,
                ActionDecisionSource.POLICY_SINGLE_ACTION,
                AgentStage.PLAN_READY,
                AgentStage.PLAN_READY,
                ExecutionStepStatus.SUCCEEDED,
                0,
                budget,
                budget,
                summary,
                null,
                null,
                at,
                at
        );
    }
}
