package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds review evidence exclusively from the authoritative formal-paper list.
 * Candidate and diagnostic verification collections are intentionally not accepted.
 */
@Component
public class ReviewInputFactory {

    private final DoiNormalizer doiNormalizer;

    public ReviewInputFactory(DoiNormalizer doiNormalizer) {
        this.doiNormalizer = Objects.requireNonNull(doiNormalizer, "doiNormalizer must not be null");
    }

    public ReviewPreparationResult prepare(AgentRunResult runResult) {
        Objects.requireNonNull(runResult, "runResult must not be null");
        return prepare(runResult.finalState());
    }

    public ReviewPreparationResult prepare(AgentState finalState) {
        Objects.requireNonNull(finalState, "finalState must not be null");
        if (!finalState.terminated()) {
            throw new IllegalArgumentException("review preparation requires a completed final state");
        }
        List<SearchResponse.PaperResult> formalPapers = finalState.verifiedPapers();
        int requiredVerifiedCount = requiredVerifiedCount(finalState.requestedCount());
        int abstractEvidenceCount = countUsableAbstracts(formalPapers);

        if (formalPapers.size() < requiredVerifiedCount) {
            return ReviewPreparationResult.ineligible(
                    ReviewEligibility.INSUFFICIENT_VERIFIED_PAPERS,
                    finalState.requestedCount(), requiredVerifiedCount, formalPapers.size(), abstractEvidenceCount
            );
        }
        if (abstractEvidenceCount < 3) {
            return ReviewPreparationResult.ineligible(
                    ReviewEligibility.INSUFFICIENT_ABSTRACTS,
                    finalState.requestedCount(), requiredVerifiedCount, formalPapers.size(), abstractEvidenceCount
            );
        }

        List<EvidencePaper> evidencePapers = new ArrayList<>();
        for (int index = 0; index < formalPapers.size(); index++) {
            SearchResponse.PaperResult formalPaper = formalPapers.get(index);
            String abstractText = sanitizeAbstract(formalPaper.paper().abstractText());
            if (abstractText == null) {
                continue;
            }
            evidencePapers.add(toEvidencePaper(formalPaper.paper(), index + 1, abstractText));
        }
        ReviewInput reviewInput = new ReviewInput(
                finalState.requestedCount(), formalPapers.size(), evidencePapers.size(), evidencePapers
        );
        return ReviewPreparationResult.eligible(requiredVerifiedCount, reviewInput);
    }

    private EvidencePaper toEvidencePaper(PaperDTO paper, int formalPosition, String abstractText) {
        String normalizedDoi = doiNormalizer.normalize(paper.doi());
        if (normalizedDoi == null || !normalizedDoi.equals(paper.doi())) {
            throw new IllegalStateException("formal paper DOI must already be normalized");
        }
        return new EvidencePaper(
                new CitationId(formalPosition),
                normalizedDoi,
                paper.title(),
                paper.authors().stream().map(PaperDTO.Author::displayName).toList(),
                paper.publicationYear(),
                paper.venue(),
                abstractText
        );
    }

    private int countUsableAbstracts(List<SearchResponse.PaperResult> formalPapers) {
        return (int) formalPapers.stream()
                .map(paper -> sanitizeAbstract(paper.paper().abstractText()))
                .filter(Objects::nonNull)
                .count();
    }

    private int requiredVerifiedCount(int requestedCount) {
        return (int) Math.ceil(requestedCount * 0.60d);
    }

    private String sanitizeAbstract(String abstractText) {
        if (abstractText == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(abstractText.length());
        abstractText.codePoints().forEach(codePoint -> {
            if (!Character.isISOControl(codePoint)
                    || codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                sanitized.appendCodePoint(codePoint);
            }
        });
        String result = sanitized.toString().strip();
        return result.isEmpty() ? null : result;
    }
}
