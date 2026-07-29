package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** Calls the generator only after the formal-paper and abstract-evidence gates pass. */
@Component
public class ReviewGenerationService {

    private final ReviewInputFactory reviewInputFactory;
    private final EvidenceReviewPromptBuilder promptBuilder;
    private final EvidenceReviewGenerator evidenceReviewGenerator;

    public ReviewGenerationService(
            ReviewInputFactory reviewInputFactory,
            EvidenceReviewPromptBuilder promptBuilder,
            EvidenceReviewGenerator evidenceReviewGenerator
    ) {
        this.reviewInputFactory = Objects.requireNonNull(reviewInputFactory, "reviewInputFactory must not be null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
        this.evidenceReviewGenerator = Objects.requireNonNull(
                evidenceReviewGenerator, "evidenceReviewGenerator must not be null");
    }

    public ReviewGenerationAttempt prepareAndGenerate(AgentRunResult runResult) {
        Objects.requireNonNull(runResult, "runResult must not be null");
        return prepareAndGenerate(runResult.finalState());
    }

    public ReviewGenerationAttempt prepareAndGenerate(AgentState finalState) {
        ReviewPreparationResult preparation = reviewInputFactory.prepare(finalState);
        if (preparation.eligibility() != ReviewEligibility.ELIGIBLE) {
            return new ReviewGenerationAttempt(preparation, Optional.empty());
        }
        ReviewInput input = preparation.reviewInput().orElseThrow();
        UntrustedReviewDraft draft = evidenceReviewGenerator.generate(promptBuilder.build(input));
        return new ReviewGenerationAttempt(preparation, Optional.of(draft));
    }
}
