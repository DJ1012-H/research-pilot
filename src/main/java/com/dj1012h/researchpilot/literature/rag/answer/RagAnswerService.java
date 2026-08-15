package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalRequest;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalProperties;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalService;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagEvidence;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagRetrieval;
import com.dj1012h.researchpilot.observability.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/** Synchronous, read-only, fail-closed RAG answer orchestration. */
@Service
public class RagAnswerService {

    private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);
    private final RagAnswerProperties properties;
    private final RagRetrievalProperties retrievalProperties;
    private final RagRetrievalService retrievalService;
    private final RagAnswerPromptBuilder promptBuilder;
    private final RagAnswerRepairPromptBuilder repairPromptBuilder;
    private final RagEvidenceAdmissionOrchestrator evidenceAdmissionOrchestrator;
    private final LlmRagAnswerGenerator generator;
    private final RagAnswerValidationPipeline validationPipeline;
    private final RagAnswerResponseAssembler responseAssembler;
    private final Clock clock;

    public RagAnswerService(
            RagAnswerProperties properties,
            RagRetrievalProperties retrievalProperties,
            RagRetrievalService retrievalService,
            RagAnswerPromptBuilder promptBuilder,
            RagAnswerRepairPromptBuilder repairPromptBuilder,
            RagEvidenceAdmissionOrchestrator evidenceAdmissionOrchestrator,
            LlmRagAnswerGenerator generator,
            RagAnswerValidationPipeline validationPipeline,
            RagAnswerResponseAssembler responseAssembler,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.retrievalProperties = Objects.requireNonNull(retrievalProperties, "retrievalProperties must not be null");
        this.retrievalService = Objects.requireNonNull(retrievalService, "retrievalService must not be null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
        this.repairPromptBuilder = Objects.requireNonNull(repairPromptBuilder, "repairPromptBuilder must not be null");
        this.evidenceAdmissionOrchestrator = Objects.requireNonNull(
                evidenceAdmissionOrchestrator, "evidenceAdmissionOrchestrator must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.validationPipeline = Objects.requireNonNull(validationPipeline, "validationPipeline must not be null");
        this.responseAssembler = Objects.requireNonNull(responseAssembler, "responseAssembler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ResearchAnswerResponse answer(ResearchQuestionRequest request) {
        long startedNanos = System.nanoTime();
        UUID requestId = RequestCorrelation.requestIdOrNew();
        int requestedTopK = requestedTopK(request);
        RagAnswerRetrievalSummary emptySummary = emptySummary(requestedTopK);
        if (!properties.isEnabled()) {
            return failed(requestId, RagAnswerFailureType.RAG_ANSWER_DISABLED, emptySummary, startedNanos, 0, 0);
        }

        NormalizedQuestion normalized;
        try {
            normalized = validate(request);
        } catch (IllegalArgumentException exception) {
            log.info("event=rag_answer_rejected requestId={} failureCode={} questionLength={} requestedTopK={}",
                    RequestCorrelation.requestIdForLog(), RagAnswerFailureType.RAG_QUESTION_INVALID,
                    safeQuestionLength(request), requestedTopK);
            return failed(requestId, RagAnswerFailureType.RAG_QUESTION_INVALID, emptySummary, startedNanos, 0, 0);
        }

        Instant deadline = clock.instant().plus(properties.getTotalTimeout());
        if (deadlineReached(deadline)) {
            return failed(requestId, RagAnswerFailureType.RAG_ANSWER_DEADLINE_EXCEEDED, emptySummary, startedNanos, 0, 0);
        }

        TrustedRagRetrieval retrieval = retrievalService.retrieveTrusted(
                new RagRetrievalRequest(
                        normalized.question(),
                        normalized.topK(),
                        normalized.fromYear(),
                        normalized.toYear(),
                        normalized.paperIds(),
                        List.of()),
                Set.of(RagSegmentType.ABSTRACT));
        RagAnswerRetrievalSummary summary = summary(retrieval, normalized.topK());
        if (retrieval.failed()) {
            return failed(requestId, RagAnswerFailureType.RAG_RETRIEVAL_FAILED, summary, startedNanos, 0, 0);
        }
        if (retrieval.evidence().isEmpty()) {
            return responseAssembler.insufficient(requestId, summary, elapsedMs(startedNanos));
        }
        if (deadlineReached(deadline)) {
            return failed(requestId, RagAnswerFailureType.RAG_ANSWER_DEADLINE_EXCEEDED, summary, startedNanos, 0, 0);
        }

        int evidenceLimit = Math.min(properties.getMaxEvidence(), retrieval.evidence().size());
        List<RagAnswerEvidence> candidateEvidence = IntStream.range(0, evidenceLimit)
                .mapToObj(index -> RagAnswerEvidence.from(index + 1, retrieval.evidence().get(index)))
                .toList();
        RagAnswerInput candidateInput = new RagAnswerInput(normalized.question(), candidateEvidence);
        RagEvidenceAdmissionResult admission;
        try {
            admission = evidenceAdmissionOrchestrator.admit(candidateInput);
        } catch (RagEvidenceAdmissionException exception) {
            return failed(
                    requestId,
                    exception.failureType(),
                    exception.failureDetailCode(),
                    summary,
                    startedNanos,
                    exception.relevanceJudgeCallCount(),
                    0, 0, 0, 0);
        } catch (RuntimeException exception) {
            return failed(
                    requestId,
                    RagAnswerFailureType.RAG_EVIDENCE_ADMISSION_INVALID,
                    summary,
                    startedNanos,
                    0, 0, 0, 0, 0);
        }
        int relevanceJudgeCalls = admission.relevanceJudgeCallCount();
        if (admission.admittedEvidence().isEmpty()) {
            return responseAssembler.insufficientAfterAdmission(
                    requestId, summary, elapsedMs(startedNanos), relevanceJudgeCalls);
        }
        int admittedEvidenceCount = admission.admittedEvidence().size();
        if (deadlineReached(deadline)) {
            return failed(
                    requestId,
                    RagAnswerFailureType.RAG_ANSWER_DEADLINE_EXCEEDED,
                    summary,
                    startedNanos,
                    relevanceJudgeCalls,
                    0,
                    admittedEvidenceCount,
                    0,
                    0);
        }

        List<RagAnswerEvidence> answerEvidence = IntStream.range(0, admittedEvidenceCount)
                .mapToObj(index -> admission.admittedEvidence().get(index).withPosition(index + 1))
                .toList();
        RagAnswerInput input = new RagAnswerInput(normalized.question(), answerEvidence);
        String initialPrompt;
        try {
            initialPrompt = promptBuilder.build(input);
        } catch (RagAnswerPromptBudgetException exception) {
            return failed(
                    requestId, RagAnswerFailureType.RAG_ANSWER_OUTPUT_INVALID, summary, startedNanos,
                    relevanceJudgeCalls, 0, admittedEvidenceCount, 0, 0);
        }
        if (deadlineReached(deadline)) {
            return failed(
                    requestId, RagAnswerFailureType.RAG_ANSWER_DEADLINE_EXCEEDED, summary, startedNanos,
                    relevanceJudgeCalls, 0, admittedEvidenceCount, 0, 0);
        }

        int answerModelCalls = 1;
        UntrustedRagAnswerDraft firstDraft;
        try {
            firstDraft = generator.generate(initialPrompt);
        } catch (ModelInvocationException | ModelNotConfiguredException exception) {
            return failed(
                    requestId, RagAnswerFailureType.RAG_GENERATION_UNAVAILABLE, summary, startedNanos,
                    relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 0);
        } catch (RuntimeException exception) {
            return failed(
                    requestId, RagAnswerFailureType.RAG_GENERATION_UNAVAILABLE, summary, startedNanos,
                    relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 0);
        }

        try {
            ValidatedRagAnswer validated = validationPipeline.validate(firstDraft, input);
            return responseAssembler.success(
                    requestId, validated, input, summary, elapsedMs(startedNanos),
                    relevanceJudgeCalls, answerModelCalls, 0);
        } catch (RagAnswerValidationException firstFailure) {
            logValidationFailure(firstFailure, 1);
            if (!firstFailure.isRetryable()) {
                return failed(
                        requestId,
                        RagAnswerFailureType.RAG_ANSWER_OUTPUT_INVALID,
                        validationDetailCode(firstFailure),
                        summary,
                        startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 0);
            }
            if (deadlineReached(deadline)) {
                return failed(
                        requestId, RagAnswerFailureType.RAG_ANSWER_DEADLINE_EXCEEDED, summary, startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 0);
            }
            String repairPrompt;
            try {
                repairPrompt = repairPromptBuilder.build(input, firstDraft, firstFailure.safeCodes());
            } catch (RuntimeException exception) {
                return failed(
                        requestId, RagAnswerFailureType.RAG_ANSWER_VALIDATION_FAILED, summary, startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 0);
            }
            if (deadlineReached(deadline)) {
                return failed(
                        requestId, RagAnswerFailureType.RAG_ANSWER_DEADLINE_EXCEEDED, summary, startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 0);
            }
            answerModelCalls = 2;
            UntrustedRagAnswerDraft repairedDraft;
            try {
                repairedDraft = generator.generate(repairPrompt);
            } catch (ModelInvocationException | ModelNotConfiguredException exception) {
                return failed(
                        requestId, RagAnswerFailureType.RAG_GENERATION_UNAVAILABLE, summary, startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 1);
            } catch (RuntimeException exception) {
                return failed(
                        requestId, RagAnswerFailureType.RAG_GENERATION_UNAVAILABLE, summary, startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 1);
            }
            try {
                ValidatedRagAnswer validated = validationPipeline.validate(repairedDraft, input);
                return responseAssembler.success(
                        requestId, validated, input, summary, elapsedMs(startedNanos),
                        relevanceJudgeCalls, answerModelCalls, 1);
            } catch (RagAnswerValidationException secondFailure) {
                logValidationFailure(secondFailure, 2);
                return failed(
                        requestId,
                        RagAnswerFailureType.RAG_ANSWER_VALIDATION_FAILED,
                        validationDetailCode(secondFailure),
                        summary,
                        startedNanos,
                        relevanceJudgeCalls, answerModelCalls, admittedEvidenceCount, admittedEvidenceCount, 1);
            }
        }
    }

    private NormalizedQuestion validate(ResearchQuestionRequest request) {
        if (request == null || request.question() == null) throw new IllegalArgumentException("question is required");
        String question = normalize(request.question());
        if (question.isEmpty() || question.length() > RagAnswerProperties.HARD_MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("question length is outside the allowed range");
        }
        int topK = request.topK() == null ? properties.getDefaultTopK() : request.topK();
        if (topK < 1 || topK > properties.getMaxTopK() || topK > retrievalProperties.getMaxTopK()) {
            throw new IllegalArgumentException("topK is outside the allowed range");
        }
        if (request.paperIds().size() > retrievalProperties.getMaxPaperIds()
                || request.paperIds().stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("paperIds are outside the allowed range");
        }
        validateYear(request.fromYear());
        validateYear(request.toYear());
        if (request.fromYear() != null && request.toYear() != null && request.fromYear() > request.toYear()) {
            throw new IllegalArgumentException("year range is inverted");
        }
        return new NormalizedQuestion(question, topK, request.fromYear(), request.toYear(), request.paperIds());
    }

    private String normalize(String value) {
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(nfc.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < nfc.length();) {
            int codePoint = nfc.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (Character.isWhitespace(codePoint) || type == Character.SPACE_SEPARATOR
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (Character.isISOControl(codePoint)) throw new IllegalArgumentException("question contains control character");
            if (pendingSpace) result.append(' ');
            pendingSpace = false;
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    private void validateYear(Integer year) {
        if (year != null && (year < retrievalProperties.getEarliestSupportedYear()
                || year > retrievalProperties.getLatestSupportedYear())) {
            throw new IllegalArgumentException("year is outside supported range");
        }
    }

    private RagAnswerRetrievalSummary summary(TrustedRagRetrieval retrieval, int requestedTopK) {
        Double lowest = retrieval.evidence().stream().map(TrustedRagEvidence::score).min(Double::compareTo).orElse(null);
        return new RagAnswerRetrievalSummary(
                retrieval.activeEmbeddingVersion(), requestedTopK,
                retrieval.qdrantCandidateCount(), retrieval.uniquePaperCandidateCount(),
                retrieval.admittedPaperCount(), retrieval.evidence().size(), retrieval.filteredCount(), lowest);
    }

    private RagAnswerRetrievalSummary emptySummary(int requestedTopK) {
        return new RagAnswerRetrievalSummary(null, Math.max(0, requestedTopK), 0, 0, 0, 0, 0, null);
    }

    private ResearchAnswerResponse failed(
            UUID requestId,
            RagAnswerFailureType failureType,
            RagAnswerRetrievalSummary summary,
            long startedNanos,
            int modelCalls,
            int repairs
    ) {
        return failed(
                requestId, failureType, null, summary, startedNanos,
                0, modelCalls, 0, 0, repairs);
    }

    private ResearchAnswerResponse failed(
            UUID requestId,
            RagAnswerFailureType failureType,
            RagAnswerRetrievalSummary summary,
            long startedNanos,
            int relevanceJudgeCalls,
            int answerModelCalls,
            int admittedEvidenceCount,
            int generationEvidenceCount,
            int repairs
    ) {
        return failed(
                requestId,
                failureType,
                null,
                summary,
                startedNanos,
                relevanceJudgeCalls,
                answerModelCalls,
                admittedEvidenceCount,
                generationEvidenceCount,
                repairs);
    }

    private ResearchAnswerResponse failed(
            UUID requestId,
            RagAnswerFailureType failureType,
            String failureDetailCode,
            RagAnswerRetrievalSummary summary,
            long startedNanos,
            int relevanceJudgeCalls,
            int answerModelCalls,
            int admittedEvidenceCount,
            int generationEvidenceCount,
            int repairs
    ) {
        return responseAssembler.failed(
                requestId,
                failureType,
                failureDetailCode,
                summary,
                elapsedMs(startedNanos),
                relevanceJudgeCalls,
                answerModelCalls,
                admittedEvidenceCount,
                generationEvidenceCount,
                repairs);
    }

    private boolean deadlineReached(Instant deadline) {
        return !clock.instant().isBefore(deadline);
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private int requestedTopK(ResearchQuestionRequest request) {
        return request == null || request.topK() == null ? properties.getDefaultTopK() : request.topK();
    }

    private int safeQuestionLength(ResearchQuestionRequest request) {
        return request == null || request.question() == null ? -1 : request.question().length();
    }

    private String validationDetailCode(RagAnswerValidationException exception) {
        return "RAG_ANSWER_" + exception.stage().name() + "_" + exception.safeCodes().getFirst();
    }

    private void logValidationFailure(RagAnswerValidationException exception, int attempt) {
        log.info(
                "event=rag_answer_validation_failed requestId={} attempt={} stage={} codes={} retryable={}",
                RequestCorrelation.requestIdForLog(),
                attempt,
                exception.stage(),
                exception.safeCodes(),
                exception.isRetryable());
    }

    private record NormalizedQuestion(
            String question,
            int topK,
            Integer fromYear,
            Integer toYear,
            List<Long> paperIds
    ) { }
}
