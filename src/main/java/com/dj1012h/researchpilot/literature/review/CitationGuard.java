package com.dj1012h.researchpilot.literature.review;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates citation syntax, membership and current-evidence ownership.
 *
 * <p>This guard does not claim semantic support or full-text fact validation.</p>
 */
@Component
public class CitationGuard {

    private final CitationIdParser citationIdParser;

    public CitationGuard(CitationIdParser citationIdParser) {
        this.citationIdParser = Objects.requireNonNull(
                citationIdParser, "citationIdParser must not be null");
    }

    public ValidatedReview validate(ReviewDraft draft, ReviewInput input) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(input, "input must not be null");
        if (draft.statements().isEmpty()) {
            throw failure("EMPTY_REVIEW", "$.statements", true);
        }

        Map<CitationId, EvidencePaper> allowed = new LinkedHashMap<>();
        for (EvidencePaper paper : input.evidencePapers()) {
            allowed.put(paper.citationId(), paper);
        }

        List<ValidatedReviewStatement> validated = new ArrayList<>();
        for (int statementIndex = 0; statementIndex < draft.statements().size(); statementIndex++) {
            ReviewStatement statement = draft.statements().get(statementIndex);
            String statementPath = "$.statements[" + statementIndex + "]";
            if (statement.citationIds().isEmpty()) {
                throw failure(
                        "MISSING_STATEMENT_CITATION",
                        statementPath + ".citationIds",
                        true
                );
            }
            List<CitationId> parsedIds = new ArrayList<>();
            Set<CitationId> seen = new HashSet<>();
            for (int citationIndex = 0; citationIndex < statement.citationIds().size(); citationIndex++) {
                String path = statementPath + ".citationIds[" + citationIndex + "]";
                CitationId parsed = citationIdParser.parse(
                        statement.citationIds().get(citationIndex), path);
                if (!allowed.containsKey(parsed)) {
                    throw failure("UNKNOWN_CITATION_ID", path, true);
                }
                if (!seen.add(parsed)) {
                    throw failure("DUPLICATE_CITATION_ID", path, true);
                }
                parsedIds.add(parsed);
            }
            validated.add(new ValidatedReviewStatement(
                    statement.type(), statement.text(), parsedIds));
        }
        return new ValidatedReview(validated);
    }

    private ReviewDraftValidationException failure(
            String code,
            String jsonPath,
            boolean retryable
    ) {
        return new ReviewDraftValidationException(
                ReviewValidationStage.CITATION_GUARD,
                List.of(new ReviewValidationIssue(code, jsonPath, retryable))
        );
    }
}
