package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.ReviewProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies deterministic evidence ordering, truncation and serialized-size limits. */
@Component
public class ReviewInputBudgeter {

    private static final int MINIMUM_EVIDENCE_PAPERS = 3;

    private final ReviewProperties properties;
    private final ReviewEvidenceSerializer serializer;

    public ReviewInputBudgeter(
            ReviewProperties properties,
            ReviewEvidenceSerializer serializer
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    public ReviewBudgetResult apply(ReviewInput input) {
        Objects.requireNonNull(input, "input must not be null");
        List<EvidencePaper> selected = new ArrayList<>();
        int maximumPapers = Math.min(
                properties.getMaxEvidencePapers(), input.evidencePapers().size());
        for (int index = 0; index < maximumPapers; index++) {
            EvidencePaper bounded = boundAbstract(input.evidencePapers().get(index));
            List<EvidencePaper> candidate = new ArrayList<>(selected);
            candidate.add(bounded);
            ReviewInput candidateInput = copyWithEvidence(input, candidate);
            if (serializer.serialize(candidateInput).length()
                    > properties.getMaxEvidenceJsonLength()) {
                break;
            }
            selected.add(bounded);
        }
        if (selected.size() < MINIMUM_EVIDENCE_PAPERS) {
            return ReviewBudgetResult.exceeded();
        }
        return ReviewBudgetResult.ready(copyWithEvidence(input, selected));
    }

    private ReviewInput copyWithEvidence(ReviewInput input, List<EvidencePaper> evidencePapers) {
        return new ReviewInput(
                input.requestedCount(),
                input.verifiedPaperCount(),
                evidencePapers.size(),
                evidencePapers
        );
    }

    private EvidencePaper boundAbstract(EvidencePaper paper) {
        String abstractText = truncateCodePoints(
                paper.abstractText(), properties.getMaxAbstractChars());
        return new EvidencePaper(
                paper.citationId(),
                paper.normalizedDoi(),
                paper.title(),
                paper.authorDisplayNames(),
                paper.publicationYear(),
                paper.venue(),
                abstractText
        );
    }

    private String truncateCodePoints(String value, int maximumCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maximumCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
    }
}
