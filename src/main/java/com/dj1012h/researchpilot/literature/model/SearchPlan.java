package com.dj1012h.researchpilot.literature.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validated and executable plan for a single OpenAlex search chain.
 *
 * <p>The LLM-generated draft is intentionally not represented by this type.
 * {@code SearchPlanBusinessValidator} must resolve relative dates, apply
 * explicit request overrides and enforce limits before creating this record.</p>
 */
public record SearchPlan(
        String originalQuery,
        String topic,
        List<String> englishKeywords,
        String searchQuery,
        Set<LanguageCode> languages,
        List<String> publicationTypes,
        SearchSort sort,
        int fromYear,
        int toYear,
        int candidateLimit,
        int resultLimit
) {

    public static final int EARLIEST_SUPPORTED_YEAR = 1900;
    public static final int MAX_CANDIDATE_LIMIT = 100;
    public static final int MAX_KEYWORD_COUNT = 10;
    public static final int MAX_KEYWORD_LENGTH = 100;
    public static final int MAX_SEARCH_QUERY_LENGTH = 300;
    public static final int MAX_RESULT_LIMIT = 50;

    public SearchPlan {
        originalQuery = requireText(originalQuery, "originalQuery");
        topic = requireText(topic, "topic");
        searchQuery = requireText(searchQuery, "searchQuery");
        englishKeywords = List.copyOf(Objects.requireNonNull(englishKeywords, "englishKeywords 不能为空"));
        languages = copyLanguages(languages);
        publicationTypes = copyTextList(publicationTypes, "publicationTypes");
        sort = Objects.requireNonNull(sort, "sort 不能为空");

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

    private static Set<LanguageCode> copyLanguages(Set<LanguageCode> languages) {
        Objects.requireNonNull(languages, "languages 不能为空");
        if (languages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("languages 不能包含空值");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(languages));
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
