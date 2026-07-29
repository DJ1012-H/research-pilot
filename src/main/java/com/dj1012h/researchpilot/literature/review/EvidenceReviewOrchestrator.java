package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Generates, validates and at most once repairs an abstract-level review.
 *
 * <p>Call counts are Java-side logical generator invocations; provider retries
 * inside one shared-model invocation do not create another repair attempt.</p>
 */
@Component
public class EvidenceReviewOrchestrator {

    private final ReviewGenerationService generationService;
    private final ReviewInputBudgeter inputBudgeter;
    private final EvidenceReviewRepairPromptBuilder repairPromptBuilder;
    private final ReviewDraftValidationPipeline validationPipeline;
    private final Clock clock;

    public EvidenceReviewOrchestrator(
            ReviewGenerationService generationService,
            ReviewInputBudgeter inputBudgeter,
            EvidenceReviewRepairPromptBuilder repairPromptBuilder,
            ReviewDraftValidationPipeline validationPipeline,
            Clock clock
    ) {
        this.generationService = Objects.requireNonNull(
                generationService, "generationService must not be null");
        this.inputBudgeter = Objects.requireNonNull(inputBudgeter, "inputBudgeter must not be null");
        this.repairPromptBuilder = Objects.requireNonNull(
                repairPromptBuilder, "repairPromptBuilder must not be null");
        this.validationPipeline = Objects.requireNonNull(
                validationPipeline, "validationPipeline must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ReviewOutcome generateValidateAndAssemble(AgentRunResult runResult) {
        Objects.requireNonNull(runResult, "runResult must not be null");
        AgentState finalState = runResult.finalState();
        ReviewPreparationResult preparation = generationService.prepare(finalState);
        if (preparation.eligibility() != ReviewEligibility.ELIGIBLE) {
            return ReviewOutcome.failed(
                    ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE,
                    0,
                    0,
                    preparation.abstractEvidenceCount(),
                    preparation.eligibility().name()
            );
        }

        ReviewBudgetResult budgetResult =
                inputBudgeter.apply(preparation.reviewInput().orElseThrow());
        if (budgetResult.status() != ReviewBudgetResult.ReviewBudgetStatus.READY) {
            return ReviewOutcome.failed(
                    ReviewOutcomeStatus.INPUT_BUDGET_EXCEEDED,
                    0,
                    0,
                    0,
                    "INPUT_BUDGET_EXCEEDED"
            );
        }
        ReviewInput input = budgetResult.reviewInput().orElseThrow();
        if (deadlineReached(finalState)) {
            return deadline(input.evidencePapers().size(), 0, 0);
        }

        UntrustedReviewDraft firstDraft;
        try {
            firstDraft = generationService.generateInitial(input);
        } catch (ReviewInputBudgetException exception) {
            return inputBudgetFailure(input.evidencePapers().size(), exception.getCode());
        } catch (RuntimeException exception) {
            return generationUnavailable(input.evidencePapers().size(), 1, 0);
        }

        try {
            ValidatedReview validated = validationPipeline.validate(firstDraft, input);
            return ReviewOutcome.generated(validated, input, 1, 0);
        } catch (ReviewDraftValidationException firstFailure) {
            if (!firstFailure.isRetryable()) {
                return validationFailed(
                        input.evidencePapers().size(), 1, 0, firstFailure.safeCodes().get(0));
            }
            if (deadlineReached(finalState)) {
                return deadline(input.evidencePapers().size(), 1, 0);
            }

            String repairPrompt;
            try {
                repairPrompt = repairPromptBuilder.build(
                        input, firstDraft, firstFailure.safeCodes());
            } catch (ReviewInputBudgetException exception) {
                return validationFailed(
                        input.evidencePapers().size(), 1, 0, exception.getCode());
            }
            if (deadlineReached(finalState)) {
                return deadline(input.evidencePapers().size(), 1, 0);
            }

            UntrustedReviewDraft repairedDraft;
            try {
                repairedDraft = generationService.generatePrompt(repairPrompt);
            } catch (RuntimeException exception) {
                return generationUnavailable(input.evidencePapers().size(), 2, 1);
            }
            try {
                ValidatedReview validated = validationPipeline.validate(repairedDraft, input);
                return ReviewOutcome.generated(validated, input, 2, 1);
            } catch (ReviewDraftValidationException secondFailure) {
                return validationFailed(
                        input.evidencePapers().size(), 2, 1, secondFailure.safeCodes().get(0));
            }
        }
    }

    private boolean deadlineReached(AgentState state) {
        Instant now = Instant.now(clock);
        return !now.isBefore(state.deadline());
    }

    private ReviewOutcome deadline(int evidenceCount, int calls, int repairs) {
        return ReviewOutcome.failed(
                ReviewOutcomeStatus.DEADLINE_EXCEEDED,
                calls,
                repairs,
                evidenceCount,
                "DEADLINE_EXCEEDED"
        );
    }

    private ReviewOutcome generationUnavailable(int evidenceCount, int calls, int repairs) {
        return ReviewOutcome.failed(
                ReviewOutcomeStatus.GENERATION_UNAVAILABLE,
                calls,
                repairs,
                evidenceCount,
                "GENERATION_UNAVAILABLE"
        );
    }

    private ReviewOutcome validationFailed(
            int evidenceCount,
            int calls,
            int repairs,
            String code
    ) {
        return ReviewOutcome.failed(
                ReviewOutcomeStatus.VALIDATION_FAILED,
                calls,
                repairs,
                evidenceCount,
                code
        );
    }

    private ReviewOutcome inputBudgetFailure(int evidenceCount, String code) {
        return ReviewOutcome.failed(
                ReviewOutcomeStatus.INPUT_BUDGET_EXCEEDED,
                0,
                0,
                evidenceCount,
                code
        );
    }
}
