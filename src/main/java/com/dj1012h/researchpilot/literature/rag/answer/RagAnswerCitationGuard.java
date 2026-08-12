package com.dj1012h.researchpilot.literature.rag.answer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Proves citation format, membership and current-request ownership only. It
 * does not prove semantic entailment or full-text factual correctness.
 */
@Component
public class RagAnswerCitationGuard {

    private static final Pattern CITATION_ID = Pattern.compile("^P([1-9][0-9]*)$");

    public ValidatedRagAnswer validate(RagAnswerDraft draft, RagAnswerInput input) {
        if (draft == null || input == null) throw new IllegalArgumentException("draft and input are required");
        Map<String, RagAnswerEvidence> allowed = new LinkedHashMap<>();
        input.evidence().forEach(evidence -> allowed.put(evidence.citationId(), evidence));
        if (draft.statements().isEmpty()) throw failure("EMPTY_STATEMENTS", "$.statements");

        List<RagAnswerStatement> validated = new ArrayList<>();
        for (int statementIndex = 0; statementIndex < draft.statements().size(); statementIndex++) {
            RagAnswerStatement statement = draft.statements().get(statementIndex);
            String path = "$.statements[" + statementIndex + "]";
            if (statement.citationIds().isEmpty()) {
                throw failure("MISSING_STATEMENT_CITATION", path + ".citationIds");
            }
            Set<String> seen = new HashSet<>();
            for (int citationIndex = 0; citationIndex < statement.citationIds().size(); citationIndex++) {
                String citationId = statement.citationIds().get(citationIndex);
                String citationPath = path + ".citationIds[" + citationIndex + "]";
                if (citationId == null || !CITATION_ID.matcher(citationId).matches()) {
                    throw failure("MALFORMED_CITATION_ID", citationPath);
                }
                if (!allowed.containsKey(citationId)) throw failure("UNKNOWN_CITATION_ID", citationPath);
                if (!seen.add(citationId)) throw failure("DUPLICATE_CITATION_ID", citationPath);
            }
            validated.add(statement);
        }
        return new ValidatedRagAnswer(validated);
    }

    private RagAnswerValidationException failure(String code, String path) {
        return new RagAnswerValidationException(
                RagAnswerValidationStage.CITATION_GUARD,
                List.of(new RagAnswerValidationIssue(code, path, true)));
    }
}
