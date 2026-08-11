package com.dj1012h.researchpilot.literature.persistence;

import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.ExecutionTraceEntry;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.persistence.entity.*;
import com.dj1012h.researchpilot.literature.persistence.mapper.LiteraturePersistenceMapper;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** MyBatis-backed audit projection. It never supplies state back to the Agent. */
@Component
@ConditionalOnProperty(name = "app.literature.persistence.enabled", havingValue = "true")
public class MyBatisLiteraturePersistenceFacade implements LiteraturePersistenceFacade {

    private final LiteraturePersistenceMapper mapper;
    private final FailureTaskFinalizer failureFinalizer;
    private final DoiNormalizer doiNormalizer;

    public MyBatisLiteraturePersistenceFacade(
            LiteraturePersistenceMapper mapper,
            FailureTaskFinalizer failureFinalizer,
            DoiNormalizer doiNormalizer
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.failureFinalizer = Objects.requireNonNull(failureFinalizer, "failureFinalizer must not be null");
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
    }

    @Override
    @Transactional
    public void createRunningTask(UUID taskId, SearchRequest request, int requestedCount, Instant startedAt) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (requestedCount < 1) throw new IllegalArgumentException("requestedCount must be positive");
        if (mapper.findTaskDatabaseId(taskId.toString()) != null) return;
        LiteratureSearchTaskEntity task = new LiteratureSearchTaskEntity(
                taskId.toString(), "RUNNING", "NOT_STARTED", null, requestedCount,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                sha256(request.query()), request.query().length(), startedAt, null);
        try {
            mapper.insertTask(task);
        } catch (DuplicateKeyException duplicate) {
            if (mapper.findTaskDatabaseId(taskId.toString()) == null) throw duplicate;
        }
    }

    @Override
    @Transactional
    public void appendExecutionStep(UUID taskId, ExecutionTraceEntry entry) {
        long databaseTaskId = requiredTaskId(taskId);
        if (mapper.stepExists(entry.traceId().toString(), entry.stepIndex()) > 0) return;
        try {
            mapper.insertStep(step(databaseTaskId, entry));
        } catch (DuplicateKeyException duplicate) {
            if (mapper.stepExists(entry.traceId().toString(), entry.stepIndex()) == 0) throw duplicate;
        }
    }

    @Override
    @Transactional
    public void finalizeSuccess(UUID taskId, AgentRunResult runResult, ReviewOutcome reviewOutcome, Instant completedAt) {
        Objects.requireNonNull(runResult, "runResult must not be null");
        Objects.requireNonNull(reviewOutcome, "reviewOutcome must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        long databaseTaskId = requiredTaskId(taskId);
        for (ExecutionTraceEntry entry : runResult.trace()) appendExecutionStep(taskId, entry);
        var state = runResult.finalState();
        for (int index = 0; index < state.planHistory().size(); index++) {
            if (mapper.planAttemptExists(databaseTaskId, index + 1) == 0) {
                mapper.insertPlanAttempt(plan(databaseTaskId, index + 1, state.planHistory().get(index)));
            }
        }
        for (int index = 0; index < state.verifiedPapers().size(); index++) {
            SearchResponse.PaperResult result = state.verifiedPapers().get(index);
            long paperId = persistFormalPaper(result);
            if (mapper.taskPaperExists(databaseTaskId, paperId) == 0) {
                mapper.insertTaskPaperResult(new LiteratureTaskPaperResultEntity(
                        databaseTaskId, paperId, index, result.relevanceScore()));
            }
        }
        for (CandidateVerificationOutcome outcome : state.verificationResults()) {
            persistEvidence(databaseTaskId, outcome, state.verifiedPapers());
        }
        Counts counts = Counts.from(state.verificationResults());
        LiteratureSearchTaskEntity completed = new LiteratureSearchTaskEntity(
                taskId.toString(), "COMPLETED", reviewOutcome.status().name(),
                state.terminationReason() == null ? null : state.terminationReason().name(),
                state.requestedCount(), Math.max(candidateCount(state), counts.total()), counts.total(), counts.verified(),
                counts.partial(), counts.unverified(), counts.rejected(), agentModelCallCount(runResult),
                reviewOutcome.modelCallCount(), reviewOutcome.repairCount(), "0".repeat(64), 0,
                state.startedAt(), completedAt);
        if (mapper.updateTaskFinal(completed) != 1) {
            throw new LiteraturePersistenceException("success finalization did not update its task");
        }
    }

    @Override
    public void finalizeFailure(UUID taskId, String failureCode, Instant completedAt) {
        failureFinalizer.finalizeFailure(taskId, failureCode, completedAt);
    }

    private void persistEvidence(
            long taskId,
            CandidateVerificationOutcome outcome,
            List<SearchResponse.PaperResult> formalPapers
    ) {
        String fingerprint = candidateFingerprint(outcome.candidate());
        if (mapper.findEvidenceId(taskId, fingerprint) != null) return;
        VerificationResult verification = outcome.verification();
        Long paperId = paperIdForOutcome(outcome, formalPapers);
        if (paperId != null) {
            mapper.updatePaperTrustState(paperId, verification.status().name(), VerificationPolicy.VERSION);
        }
        LiteratureVerificationEvidenceEntity evidence = new LiteratureVerificationEvidenceEntity(
                taskId, paperId, fingerprint, verification.status().name(), verification.source().name(),
                verification.referenceDoi(), verification.evidenceScore(), VerificationPolicy.VERSION,
                canonical(verification.reasons(), 2000));
        try {
            mapper.insertEvidence(evidence);
        } catch (DuplicateKeyException duplicate) {
            if (mapper.findEvidenceId(taskId, fingerprint) == null) throw duplicate;
        }
        Long evidenceId = mapper.findEvidenceId(taskId, fingerprint);
        for (int index = 0; index < verification.fieldResults().size(); index++) {
            if (mapper.fieldEvidenceExists(evidenceId, index + 1) == 0) {
                VerificationResult.FieldVerification field = verification.fieldResults().get(index);
                mapper.insertFieldEvidence(new LiteratureVerificationFieldEvidenceEntity(
                        evidenceId, index + 1, field.field(), field.status().name(),
                        truncate(field.candidateValue(), 1000), truncate(field.referenceValue(), 1000),
                        field.similarity(), truncate(field.reason(), 128)));
            }
        }
    }

    private Long paperIdForOutcome(
            CandidateVerificationOutcome outcome,
            List<SearchResponse.PaperResult> formalPapers
    ) {
        String candidateDoi = doiNormalizer.normalize(outcome.candidate().doi());
        if (candidateDoi != null) {
            Long existing = mapper.findPaperId(candidateDoi);
            if (existing != null) return existing;
        }
        if (outcome.verification().status() != VerificationResult.VerificationStatus.VERIFIED) return null;
        return formalPapers.stream()
                .filter(paper -> paper.verification() == outcome.verification())
                .map(SearchResponse.PaperResult::paper)
                .map(PaperDTO::doi)
                .map(mapper::findPaperId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private long persistFormalPaper(SearchResponse.PaperResult result) {
        if (result.verification().status() != VerificationResult.VerificationStatus.VERIFIED
                || result.paper().doi() == null || result.paper().doi().isBlank()) {
            throw new IllegalArgumentException("only verified papers with normalized DOI may be persisted");
        }
        Long existing = mapper.findPaperId(result.paper().doi());
        PaperDTO paper = result.paper();
        LiteraturePaperEntity entity = new LiteraturePaperEntity(
                paper.doi(), truncate(paper.openAlexId(), 64), truncate(paper.title(), 1000),
                canonical(paper.authors().stream().map(PaperDTO.Author::displayName).toList(), 4000),
                paper.publicationYear(), truncate(paper.venue(), 1000), truncate(paper.publicationType(), 128),
                truncate(paper.language(), 32), paper.abstractText(), paper.citedByCount(), paper.source().name(),
                VerificationResult.VerificationStatus.VERIFIED.name(), VerificationPolicy.VERSION);
        if (existing != null) {
            mapper.updateVerifiedPaper(existing, entity);
            return existing;
        }
        try {
            mapper.insertPaper(entity);
        } catch (DuplicateKeyException duplicate) {
            if (mapper.findPaperId(paper.doi()) == null) throw duplicate;
        }
        Long id = mapper.findPaperId(paper.doi());
        if (id == null) throw new LiteraturePersistenceException("paper insert did not produce an identifier");
        mapper.updateVerifiedPaper(id, entity);
        return id;
    }

    private long requiredTaskId(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Long id = mapper.findTaskDatabaseId(taskId.toString());
        if (id == null) throw new LiteraturePersistenceException("running task is missing");
        return id;
    }

    private LiteraturePlanAttemptEntity plan(long taskId, int attemptNo, SearchPlan plan) {
        return new LiteraturePlanAttemptEntity(taskId, attemptNo, "ACCEPTED", "search-plan-v1", "search-plan-v1",
                "search-plan-v1", truncate(plan.topic(), 200), plan.searchQuery(),
                canonical(plan.englishKeywords(), 1200), canonical(plan.languages().stream().map(Enum::name)
                        .sorted().toList(), 256), canonical(plan.publicationTypes(), 512), plan.sort().name(),
                plan.fromYear(), plan.toYear(), plan.candidateLimit(), plan.resultLimit());
    }

    private LiteratureAgentStepEntity step(long taskId, ExecutionTraceEntry entry) {
        var before = entry.budgetBefore();
        var after = entry.budgetAfter();
        return new LiteratureAgentStepEntity(taskId, entry.traceId().toString(), entry.stepIndex(), entry.action().name(),
                entry.decisionSource() == null ? null : entry.decisionSource().name(), entry.stageBefore().name(),
                entry.stageAfter().name(), entry.status().name(), entry.elapsedMs(), before.searchRoundCount(),
                after.searchRoundCount(), before.planAdjustmentCount(), after.planAdjustmentCount(),
                before.businessStepCount(), after.businessStepCount(), before.uniqueCandidateCount(),
                after.uniqueCandidateCount(), before.crossrefCallCount(), after.crossrefCallCount(),
                before.deadlineExceeded(), after.deadlineExceeded(), entry.observationSummary(),
                truncate(entry.failureCode(), 128),
                entry.terminationReason() == null ? null : entry.terminationReason().name(), entry.startedAt(), entry.finishedAt());
    }

    private static int candidateCount(com.dj1012h.researchpilot.literature.agent.AgentState state) {
        int observed = state.observations().stream()
                .filter(observation -> observation.action().name().equals("SEARCH_OPENALEX"))
                .mapToInt(observation -> observation.candidateCount()).sum();
        return Math.max(observed, state.uniqueCandidateCount());
    }

    private static int agentModelCallCount(AgentRunResult runResult) {
        return (int) runResult.trace().stream()
                .filter(entry -> entry.decisionSource() != null)
                .filter(entry -> entry.decisionSource().name().equals("MODEL_PROPOSED"))
                .count();
    }

    private static String candidateFingerprint(CandidatePaper candidate) {
        String firstAuthor = candidate.authors().isEmpty() ? "" : candidate.authors().getFirst().displayName();
        return sha256(String.join("|", safe(candidate.openAlexId()), safe(candidate.doi()), safe(candidate.title()),
                safe(firstAuthor), candidate.publicationYear() == null ? "" : candidate.publicationYear().toString()));
    }

    private static String canonical(List<String> values, int maximumLength) {
        return truncate(values.stream().map(MyBatisLiteraturePersistenceFacade::safe)
                .collect(Collectors.joining("|")), maximumLength);
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String truncate(String value, int maximumLength) {
        if (value == null) return null;
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Counts(int verified, int partial, int unverified, int rejected) {
        static Counts from(List<CandidateVerificationOutcome> outcomes) {
            int verified = 0, partial = 0, unverified = 0, rejected = 0;
            for (CandidateVerificationOutcome outcome : outcomes) {
                switch (outcome.verification().status()) {
                    case VERIFIED -> verified++;
                    case PARTIALLY_VERIFIED -> partial++;
                    case NOT_CHECKED, NOT_FOUND, SOURCE_UNAVAILABLE -> unverified++;
                    case CONFLICTED, REJECTED -> rejected++;
                }
            }
            return new Counts(verified, partial, unverified, rejected);
        }
        int total() { return verified + partial + unverified + rejected; }
    }
}
