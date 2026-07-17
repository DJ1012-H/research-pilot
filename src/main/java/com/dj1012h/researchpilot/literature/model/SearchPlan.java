package com.dj1012h.researchpilot.literature.model;

import java.util.List;
import java.util.Objects;

/**
 * Validated and executable plan for a single OpenAlex search chain.
 *
 * <p>The LLM-generated draft is intentionally not represented by this type.
 * A future {@code SearchPlanValidator} must resolve relative dates, apply
 * explicit request overrides and enforce limits before creating this record.</p>
 */
public record SearchPlan(
        String originalQuery,
        String topic,
        List<String> englishKeywords,
        String searchQuery,
        List<String> languages,
        List<String> publicationTypes,
        int fromYear,
        int toYear,
        int candidateLimit,
        int resultLimit
) {

    public static final int EARLIEST_SUPPORTED_YEAR = 1900;
    public static final int MAX_CANDIDATE_LIMIT = 100;
    public static final int MAX_RESULT_LIMIT = 50;

    public SearchPlan {
        originalQuery = requireText(originalQuery, "originalQuery");
        topic = requireText(topic, "topic");
        searchQuery = requireText(searchQuery, "searchQuery");
        englishKeywords = List.copyOf(Objects.requireNonNull(englishKeywords, "englishKeywords 不能为空"));
        languages = copyTextList(languages, "languages");
        publicationTypes = copyTextList(publicationTypes, "publicationTypes");

        if (englishKeywords.isEmpty()) {
            throw new IllegalArgumentException("englishKeywords 不能为空");
        }
        if (englishKeywords.stream().anyMatch(keyword -> keyword == null || keyword.isBlank())) {
            throw new IllegalArgumentException("englishKeywords 不能包含空值");
        }
        if (fromYear < EARLIEST_SUPPORTED_YEAR) {
            throw new IllegalArgumentException("fromYear 不能早于 " + EARLIEST_SUPPORTED_YEAR);
        }
        if (toYear < fromYear) {
            throw new IllegalArgumentException("toYear 不能早于 fromYear");
        }
        if (resultLimit < 1 || resultLimit > MAX_RESULT_LIMIT) {
            throw new IllegalArgumentException("resultLimit 必须在 1 到 " + MAX_RESULT_LIMIT + " 之间");
        }
        if (candidateLimit < resultLimit || candidateLimit > MAX_CANDIDATE_LIMIT) {
            throw new IllegalArgumentException(
                    "candidateLimit 必须大于等于 resultLimit 且不超过 " + MAX_CANDIDATE_LIMIT
            );
        }
    }

    private static List<String> copyTextList(List<String> values, String field) {
        List<String> copied = List.copyOf(Objects.requireNonNull(values, field + " 不能为空"));
        if (copied.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " 不能包含空值");
        }
        return copied;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
