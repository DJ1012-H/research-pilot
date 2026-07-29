package com.dj1012h.researchpilot.literature.review;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Enforces review rules that are clearer outside JSON Schema. */
@Component
public class ReviewDraftBusinessValidator {

    private static final Pattern DOI = Pattern.compile("(?i)\\b10\\.\\d{4,9}/\\S+");
    private static final Pattern URL = Pattern.compile("(?i)https?://|www\\.");
    private static final Pattern YEAR = Pattern.compile("\\b(?:18|19|20|21)\\d{2}\\b");
    private static final Pattern HTML = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+]\\s*\\([^)]*\\)");
    private static final Pattern MODEL_CITATION_MARKER = Pattern.compile("\\[P\\d+]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_CONTROL_TEXT = Pattern.compile(
            "(?i)\\b(?:tool[_ ]?call|function[_ ]?call|agentstate|reviewinput|system prompt"
                    + "|verified|partially_verified)\\b|参考文献|引用已验证|核验通过|验证通过");

    public ReviewDraft validate(ReviewDraft draft, ReviewInput input) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(input, "input must not be null");
        if (draft.statements().isEmpty()) {
            throw failure("EMPTY_REVIEW", "$.statements");
        }
        for (int index = 0; index < draft.statements().size(); index++) {
            ReviewStatement statement = draft.statements().get(index);
            String path = "$.statements[" + index + "].text";
            if (statement.text().isBlank()) {
                throw failure("EMPTY_TEXT", path);
            }
            if (DOI.matcher(statement.text()).find()
                    || URL.matcher(statement.text()).find()
                    || YEAR.matcher(statement.text()).find()
                    || HTML.matcher(statement.text()).find()
                    || MARKDOWN_LINK.matcher(statement.text()).find()
                    || MODEL_CITATION_MARKER.matcher(statement.text()).find()
                    || FORBIDDEN_CONTROL_TEXT.matcher(statement.text()).find()) {
                throw failure("BIBLIOGRAPHIC_TEXT_NOT_ALLOWED", path);
            }
            String normalizedText = statement.text().toLowerCase(Locale.ROOT);
            boolean containsEvidenceTitle = input.evidencePapers().stream()
                    .map(EvidencePaper::title)
                    .filter(value -> value.length() >= 8)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedText::contains);
            boolean containsEvidenceAuthor = input.evidencePapers().stream()
                    .flatMap(paper -> paper.authorDisplayNames().stream())
                    .filter(value -> value.length() >= 2)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedText::contains);
            if (containsEvidenceTitle || containsEvidenceAuthor) {
                throw failure("BIBLIOGRAPHIC_TEXT_NOT_ALLOWED", path);
            }
        }
        return draft;
    }

    private ReviewDraftValidationException failure(String code, String path) {
        return new ReviewDraftValidationException(
                ReviewValidationStage.BUSINESS_RULE,
                List.of(new ReviewValidationIssue(code, path, true))
        );
    }
}
