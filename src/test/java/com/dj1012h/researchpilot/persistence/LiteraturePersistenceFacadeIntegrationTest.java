package com.dj1012h.researchpilot.persistence;

import com.dj1012h.researchpilot.literature.agent.AgentAction;
import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentStage;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.agent.BudgetUsageSnapshot;
import com.dj1012h.researchpilot.literature.agent.ExecutionStepStatus;
import com.dj1012h.researchpilot.literature.agent.ExecutionTraceEntry;
import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceFacade;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import com.dj1012h.researchpilot.literature.review.ReviewOutcomeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:runtime_persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
        "app.literature.persistence.enabled=true"
})
class LiteraturePersistenceFacadeIntegrationTest {

    @Autowired private LiteraturePersistenceFacade persistence;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistOneTaskAndIdempotentTraceStepWithoutQueryText() {
        UUID taskId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-05T00:00:00Z");
        SearchRequest request = new SearchRequest("sensitive research query", null, null, 1);

        persistence.createRunningTask(taskId, request, 1, at);
        persistence.createRunningTask(taskId, request, 1, at);
        ExecutionTraceEntry entry = new ExecutionTraceEntry(
                taskId, 0, AgentAction.COMPLETE, null, AgentStage.PLAN_READY, AgentStage.COMPLETED,
                ExecutionStepStatus.SUCCEEDED, 0, new BudgetUsageSnapshot(0, 0, 0, 0, 0, false),
                new BudgetUsageSnapshot(0, 0, 0, 0, 0, false), "completed", null, null, at, at);
        persistence.appendExecutionStep(taskId, entry);
        persistence.appendExecutionStep(taskId, entry);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM literature_search_task WHERE task_id = ?", Integer.class, taskId.toString()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM literature_agent_step WHERE trace_id = ? AND step_index = 0",
                Integer.class, taskId.toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT query_hash FROM literature_search_task WHERE task_id = ?", String.class, taskId.toString()))
                .isNotEqualTo(request.query());
    }

    @Test
    void shouldFinalizeVerifiedEvidenceAndRemainIdempotent() {
        UUID taskId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-05T00:00:00Z");
        SearchRequest request = new SearchRequest("persistence test query", null, null, 1);
        persistence.createRunningTask(taskId, request, 1, at);
        SearchPlan plan = plan(request.query());
        VerificationResult verification = new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED, 1.0,
                VerificationResult.VerificationSource.CROSSREF, "10.1000/persisted",
                List.of(new VerificationResult.FieldVerification("doi",
                        VerificationResult.FieldStatus.MATCHED, "10.1000/persisted", "10.1000/persisted", 1.0,
                        "DOI_EQUAL")), List.of("VERIFIED"));
        CandidatePaper candidate = new CandidatePaper("W-persisted", "10.1000/persisted", "Persisted paper",
                List.of(new CandidatePaper.Author(null, "Ada Lovelace", null)), "Journal", null, 2026,
                "article", "en", 0, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
        CandidateVerificationOutcome outcome = new CandidateVerificationOutcome(candidate,
                new com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata(
                        "10.1000/persisted", "Persisted paper", List.of("Ada Lovelace"), 2026,
                        "Journal", "article", null), verification);
        PaperDTO paper = new PaperDTO("W-persisted", "10.1000/persisted", "Persisted paper",
                List.of(new PaperDTO.Author(null, "Ada Lovelace", null)), 2026, "Journal", List.of(), "article",
                null, null, "en", List.of(), 0, PaperDTO.LiteratureSource.OPENALEX);
        AgentState state = new AgentState(request.query(), 1, plan, List.of(plan), AgentStage.COMPLETED,
                AgentAction.COMPLETE, List.of(), List.of(), List.of(outcome),
                List.of(new SearchResponse.PaperResult(paper, 1.0, verification)), 0, 0, 0, 0, 0,
                Set.of(), 0, List.of(), at, at.plus(Duration.ofSeconds(30)), at,
                TerminationReason.TARGET_REACHED, "completed");
        AgentRunResult runResult = mock(AgentRunResult.class);
        when(runResult.finalState()).thenReturn(state);
        when(runResult.trace()).thenReturn(List.of());
        ReviewOutcome review = ReviewOutcome.failed(ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE, 0, 0, 0, "TEST");

        persistence.finalizeSuccess(taskId, runResult, review, at);
        persistence.finalizeSuccess(taskId, runResult, review, at);

        assertThat(count("literature_plan_attempt", taskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM literature_paper WHERE normalized_doi = '10.1000/persisted'", Integer.class))
                .isEqualTo(1);
        assertThat(count("literature_task_paper_result", taskId)).isEqualTo(1);
        assertThat(count("literature_verification_evidence", taskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM literature_verification_field_evidence", Integer.class))
                .isGreaterThanOrEqualTo(1);

        UUID failedTask = UUID.randomUUID();
        persistence.createRunningTask(failedTask, request, 1, at);
        persistence.finalizeFailure(failedTask, "TEST_FAILURE", at);
        assertThat(jdbcTemplate.queryForObject("SELECT task_status FROM literature_search_task WHERE task_id = ?",
                String.class, failedTask.toString())).isEqualTo("FAILED");
    }

    private int count(String table, UUID taskId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE search_task_id = (SELECT id FROM literature_search_task WHERE task_id = ?)",
                Integer.class, taskId.toString());
    }

    private SearchPlan plan(String query) {
        return new SearchPlan(query, "Persistence testing", List.of("persistence"), "persistence",
                Set.of(LanguageCode.EN), List.of("article"), SearchSort.NEWEST, 2025, 2026, 1, 1);
    }
}
