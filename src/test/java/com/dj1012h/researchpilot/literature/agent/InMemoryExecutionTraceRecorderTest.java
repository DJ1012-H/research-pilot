package com.dj1012h.researchpilot.literature.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class InMemoryExecutionTraceRecorderTest {

    private static final Instant AT = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void shouldIsolateTracesAssignContinuousIndexesAndReturnImmutableSnapshots() {
        InMemoryExecutionTraceRecorder recorder = new InMemoryExecutionTraceRecorder();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        recorder.record(first, draft("first"));
        recorder.record(first, draft("second"));
        recorder.record(second, draft("other"));

        assertThat(recorder.entries(first)).extracting(ExecutionTraceEntry::stepIndex)
                .containsExactly(0, 1);
        assertThat(recorder.entries(second)).extracting(ExecutionTraceEntry::stepIndex)
                .containsExactly(0);
        assertThatThrownBy(() -> recorder.entries(first).add(recorder.entries(first).getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNotLoseConcurrentWrites() throws Exception {
        InMemoryExecutionTraceRecorder recorder = new InMemoryExecutionTraceRecorder();
        UUID traceId = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> writes = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                writes.add(() -> {
                    recorder.record(traceId, draft("safe summary"));
                    return null;
                });
            }
            executor.invokeAll(writes).forEach(future -> {
                try {
                    future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        }

        assertThat(recorder.entries(traceId)).hasSize(100);
        assertThat(recorder.entries(traceId)).extracting(ExecutionTraceEntry::stepIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 100).boxed().toList());
    }

    @Test
    void shouldBoundSummaryAndKeepSensitivePayloadsOutOfTheTraceContract() {
        InMemoryExecutionTraceRecorder recorder = new InMemoryExecutionTraceRecorder();
        String longSummary = "x".repeat(800);

        ExecutionTraceEntry entry = recorder.record(UUID.randomUUID(), draft(longSummary));

        assertThat(entry.elapsedMs()).isZero();
        assertThat(entry.observationSummary()).hasSize(500);
        assertThat(ExecutionTraceEntry.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("prompt", "rawModelOutput", "providerPayload", "apiKey", "token");
    }

    @Test
    void shouldLogOnlySafeAgentStepPerformanceFields(CapturedOutput output) {
        InMemoryExecutionTraceRecorder recorder = new InMemoryExecutionTraceRecorder();
        UUID traceId = UUID.randomUUID();
        String privateSummary = "private-summary-must-not-appear";

        recorder.record(traceId, draft(privateSummary));

        assertThat(output)
                .contains("event=literature_agent_step")
                .contains("action=SEARCH_OPENALEX")
                .contains("status=SUCCEEDED")
                .contains("durationMs=0")
                .doesNotContain(traceId.toString())
                .doesNotContain(privateSummary);
    }

    @Test
    void shouldRejectDiscontinuousStagesAndDecreasingBudgetUsage() {
        InMemoryExecutionTraceRecorder recorder = new InMemoryExecutionTraceRecorder();
        UUID traceId = UUID.randomUUID();
        recorder.record(traceId, draft("first"));
        BudgetUsageSnapshot lower = new BudgetUsageSnapshot(0, 0, 0, 0, 0, false);
        BudgetUsageSnapshot higher = new BudgetUsageSnapshot(1, 0, 1, 0, 0, false);

        assertThatThrownBy(() -> recorder.record(traceId, new ExecutionTraceDraft(
                AgentAction.DEDUPLICATE_CANDIDATES,
                ActionDecisionSource.POLICY_SINGLE_ACTION,
                AgentStage.SEARCHING,
                AgentStage.CANDIDATES_DEDUPLICATED,
                ExecutionStepStatus.SUCCEEDED,
                0,
                lower,
                lower,
                "bad stage",
                null,
                null,
                AT,
                AT
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("continuous");

        assertThatThrownBy(() -> recorder.record(UUID.randomUUID(), new ExecutionTraceDraft(
                AgentAction.SEARCH_OPENALEX,
                ActionDecisionSource.POLICY_SINGLE_ACTION,
                AgentStage.PLAN_READY,
                AgentStage.CANDIDATES_RETRIEVED,
                ExecutionStepStatus.SUCCEEDED,
                0,
                higher,
                lower,
                "decreasing budget",
                null,
                null,
                AT,
                AT
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("monotonic");
    }

    private ExecutionTraceDraft draft(String summary) {
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
                AT,
                AT
        );
    }
}
