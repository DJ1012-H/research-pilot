package com.dj1012h.researchpilot.literature.rag.answer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rejects public-text leakage and internal-control language before citations are checked. */
@Component
public class RagAnswerBusinessValidator {

    private static final Pattern DOI = Pattern.compile("(?i)\\b10\\.\\d{4,9}/\\S+");
    private static final Pattern URL = Pattern.compile("(?i)https?://|www\\.");
    private static final Pattern YEAR = Pattern.compile("\\b(?:18|19|20|21)\\d{2}\\b");
    private static final Pattern HTML = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+]\\s*\\([^)]*\\)");
    private static final Pattern MODEL_CITATION = Pattern.compile("\\[P\\d+\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL = Pattern.compile(
            "(?i)\\b(?:tool[_ ]?call|function[_ ]?call|system prompt|developer message|prompt injection|"
                    + "internal state|verification status|agent state|citation guard|json schema|verified|"
                    + "partially_verified)\\b|参考文献|引用已验证|核验通过|验证通过");

    public RagAnswerDraft validate(RagAnswerDraft draft, RagAnswerInput input) {
        if (draft == null || input == null) throw new IllegalArgumentException("draft and input are required");
        if (draft.statements().isEmpty()) throw failure("EMPTY_STATEMENTS", "$.statements");
        for (int index = 0; index < draft.statements().size(); index++) {
            RagAnswerStatement statement = draft.statements().get(index);
            String path = "$.statements[" + index + "].text";
            if (statement.text().isBlank()) throw failure("EMPTY_STATEMENT", path);
            if (DOI.matcher(statement.text()).find()
                    || URL.matcher(statement.text()).find()
                    || YEAR.matcher(statement.text()).find()
                    || HTML.matcher(statement.text()).find()
                    || MARKDOWN_LINK.matcher(statement.text()).find()
                    || MODEL_CITATION.matcher(statement.text()).find()
                    || INTERNAL.matcher(statement.text()).find()) {
                throw failure("PUBLIC_TEXT_NOT_ALLOWED", path);
            }
            String normalized = statement.text().toLowerCase(Locale.ROOT);
            boolean titleLeak = input.evidence().stream()
                    .map(RagAnswerEvidence::title)
                    .filter(value -> value.length() >= 8)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(normalized::contains);
            boolean authorLeak = input.evidence().stream()
                    .flatMap(value -> value.authors().stream())
                    .filter(value -> value.length() >= 2)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(normalized::contains);
            if (titleLeak || authorLeak) throw failure("BIBLIOGRAPHIC_TEXT_NOT_ALLOWED", path);
        }
        return draft;
    }

    private RagAnswerValidationException failure(String code, String path) {
        return new RagAnswerValidationException(
                RagAnswerValidationStage.BUSINESS_RULE,
                List.of(new RagAnswerValidationIssue(code, path, true)));
    }
}
