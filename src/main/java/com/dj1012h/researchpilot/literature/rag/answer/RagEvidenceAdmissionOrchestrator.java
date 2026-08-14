package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Executes at most one relevance-model call and fails closed on every invalid state. */
@Component
public class RagEvidenceAdmissionOrchestrator {
    private final RagEvidenceAdmissionPromptBuilder promptBuilder;
    private final LlmRagEvidenceRelevanceJudge relevanceJudge;
    private final RagEvidenceAdmissionValidator validator;

    public RagEvidenceAdmissionOrchestrator(
            RagEvidenceAdmissionPromptBuilder promptBuilder,
            LlmRagEvidenceRelevanceJudge relevanceJudge,
            RagEvidenceAdmissionValidator validator
    ) {
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
        this.relevanceJudge = Objects.requireNonNull(relevanceJudge, "relevanceJudge must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    public RagEvidenceAdmissionResult admit(RagAnswerInput candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        String prompt;
        try {
            prompt = promptBuilder.build(candidates);
        } catch (RuntimeException exception) {
            throw new RagEvidenceAdmissionException(
                    RagAnswerFailureType.RAG_EVIDENCE_ADMISSION_INVALID, 0, exception);
        }

        String raw;
        try {
            raw = relevanceJudge.judge(prompt);
        } catch (ModelInvocationException | ModelNotConfiguredException exception) {
            throw new RagEvidenceAdmissionException(
                    RagAnswerFailureType.RAG_RELEVANCE_JUDGE_UNAVAILABLE, 1, exception);
        } catch (RuntimeException exception) {
            throw new RagEvidenceAdmissionException(
                    RagAnswerFailureType.RAG_RELEVANCE_JUDGE_UNAVAILABLE, 1, exception);
        }

        RagEvidenceAdmissionDecision decision;
        try {
            decision = validator.validate(raw, candidates);
        } catch (RuntimeException exception) {
            throw new RagEvidenceAdmissionException(
                    RagAnswerFailureType.RAG_EVIDENCE_ADMISSION_INVALID, 1, exception);
        }
        if (!decision.relevant()) {
            return new RagEvidenceAdmissionResult(List.of(), 1);
        }
        Map<String, RagAnswerEvidence> evidenceById = candidates.evidence().stream()
                .collect(Collectors.toMap(
                        RagAnswerEvidence::citationId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<RagAnswerEvidence> admitted = decision.admittedEvidenceIds().stream()
                .map(evidenceById::get)
                .toList();
        return new RagEvidenceAdmissionResult(admitted, 1);
    }
}
