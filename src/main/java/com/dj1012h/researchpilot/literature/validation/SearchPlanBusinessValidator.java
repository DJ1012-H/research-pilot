package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.SearchConstraintField;
import com.dj1012h.researchpilot.literature.model.SearchConstraintOrigins;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SearchPlanBusinessValidator {

    private static final Set<String> PUBLICATION_TYPES = Set.of(
            "article",
            "review",
            "preprint",
            "book-chapter",
            "dissertation",
            "report"
    );

    private static final Set<String> GENERIC_QUERIES = Set.of(
            "paper",
            "papers",
            "article",
            "articles",
            "literature",
            "论文",
            "文献",
            "文章"
    );

    private final LiteratureSearchProperties properties;

    public SearchPlanBusinessValidator(LiteratureSearchProperties properties) {
        this.properties = properties;
        validateConfiguration();
    }

    public SearchPlan validate(
            SearchPlanGenerationContext context,
            SearchPlanDraft draft
    ) {
        return validateWithOrigins(context, draft).plan();
    }

    public SearchPlanValidationResult validateWithOrigins(
            SearchPlanGenerationContext context,
            SearchPlanDraft draft
    ) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为空");
        }
        if (draft == null) {
            throw new IllegalArgumentException("draft 不能为空");
        }

        SearchRequest request = context.request();
        String topic = requiredText(draft.topic(), "topic", 200);
        List<String> keywords = normalizeKeywords(draft.englishKeywords());
        String searchQuery = requiredText(
                draft.searchQuery(), "searchQuery", SearchPlan.MAX_SEARCH_QUERY_LENGTH);
        validateQueryRelationship(searchQuery, keywords);

        Set<LanguageCode> languages = normalizeLanguages(draft.languages());
        ResolvedValue<List<String>> publicationTypes = resolvePublicationTypes(draft.publicationTypes());
        ResolvedValue<SearchSort> sort = resolveSort(draft.sort());

        ResolvedYears years = resolveYears(context, request, draft);
        ResolvedValue<Integer> resultLimit = resolveResultLimit(request, draft);
        int candidateLimit = calculateCandidateLimit(resultLimit.value());

        try {
            SearchPlan plan = new SearchPlan(
                    request.query(),
                    topic,
                    keywords,
                    searchQuery,
                    languages,
                    publicationTypes.value(),
                    sort.value(),
                    years.fromYear().value(),
                    years.toYear().value(),
                    candidateLimit,
                    resultLimit.value()
            );
            return new SearchPlanValidationResult(plan, origins(years, resultLimit, publicationTypes, sort));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw failure(
                    "INVALID_SEARCH_PLAN",
                    "$",
                    "校验后的字段仍不满足 SearchPlan 不变量",
                    true
            );
        }
    }

    private List<String> normalizeKeywords(List<String> values) {
        if (values == null) {
            throw failure("NO_VALID_KEYWORDS", "$.englishKeywords", "关键词不能为空", true);
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = normalizeWhitespace(value);
            if (!normalized.isBlank()) {
                unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        if (unique.isEmpty()) {
            throw failure("NO_VALID_KEYWORDS", "$.englishKeywords", "没有有效英文关键词", true);
        }
        if (unique.size() > SearchPlan.MAX_KEYWORD_COUNT) {
            throw failure(
                    "TOO_MANY_KEYWORDS",
                    "$.englishKeywords",
                    "关键词不能超过 " + SearchPlan.MAX_KEYWORD_COUNT + " 个",
                    true
            );
        }
        if (unique.values().stream().anyMatch(value -> value.length() > SearchPlan.MAX_KEYWORD_LENGTH)) {
            throw failure(
                    "KEYWORD_TOO_LONG",
                    "$.englishKeywords",
                    "单个关键词长度不能超过 " + SearchPlan.MAX_KEYWORD_LENGTH,
                    true
            );
        }
        return List.copyOf(unique.values());
    }

    private void validateQueryRelationship(String searchQuery, List<String> keywords) {
        String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);
        if (GENERIC_QUERIES.contains(normalizedQuery)) {
            throw failure(
                    "SEARCH_QUERY_TOO_GENERIC",
                    "$.searchQuery",
                    "检索式不能只包含通用文献词",
                    true
            );
        }
        boolean related = keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(keyword -> normalizedQuery.contains(keyword)
                        || significantTokens(keyword).stream().anyMatch(normalizedQuery::contains));
        if (!related) {
            throw failure(
                    "QUERY_KEYWORD_MISMATCH",
                    "$.searchQuery",
                    "检索式必须与至少一个关键词相关",
                    true
            );
        }
    }

    private List<String> significantTokens(String keyword) {
        List<String> tokens = new ArrayList<>();
        for (String token : keyword.split("[^a-z0-9]+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Set<LanguageCode> normalizeLanguages(List<String> values) {
        Set<LanguageCode> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw failure("UNSUPPORTED_LANGUAGE", "$.languages", "语言不能为空", true);
            }
            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "en" -> result.add(LanguageCode.EN);
                case "zh" -> result.add(LanguageCode.ZH);
                default -> throw failure(
                        "UNSUPPORTED_LANGUAGE",
                        "$.languages",
                        "只支持 en 和 zh",
                        true
                );
            }
        }
        return result;
    }

    private ResolvedValue<List<String>> resolvePublicationTypes(List<String> values) {
        if (values.isEmpty()) {
            return new ResolvedValue<>(List.of("article"), ConstraintOrigin.SYSTEM_DEFAULT);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw failure(
                        "UNSUPPORTED_PUBLICATION_TYPE",
                        "$.publicationTypes",
                        "文献类型不能为空",
                        true
                );
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!PUBLICATION_TYPES.contains(normalized)) {
                throw failure(
                        "UNSUPPORTED_PUBLICATION_TYPE",
                        "$.publicationTypes",
                        "文献类型不在业务白名单中",
                        true
                );
            }
            result.add(normalized);
        }
        return new ResolvedValue<>(List.copyOf(result), ConstraintOrigin.MODEL_DERIVED);
    }

    private ResolvedValue<SearchSort> resolveSort(String value) {
        if (value == null) {
            return new ResolvedValue<>(SearchSort.RELEVANCE, ConstraintOrigin.SYSTEM_DEFAULT);
        }
        SearchSort sort = switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "relevance" -> SearchSort.RELEVANCE;
            case "newest" -> SearchSort.NEWEST;
            case "most_cited" -> SearchSort.MOST_CITED;
            default -> throw failure("INVALID_SORT", "$.sort", "排序值不受支持", true);
        };
        return new ResolvedValue<>(sort, ConstraintOrigin.MODEL_DERIVED);
    }

    private ResolvedYears resolveYears(
            SearchPlanGenerationContext context,
            SearchRequest request,
            SearchPlanDraft draft
    ) {
        if (draft.recentYears() != null
                && (draft.fromYear() != null || draft.toYear() != null)) {
            throw failure(
                    "CONFLICTING_TIME_CONSTRAINTS",
                    "$",
                    "相对年份与明确年份不能同时出现",
                    true
            );
        }
        if (draft.recentYears() != null
                && (draft.recentYears() < 1 || draft.recentYears() > 100)) {
            throw failure("INVALID_RECENT_YEARS", "$.recentYears", "相对年份超出范围", true);
        }

        ResolvedValue<Integer> fromYear = resolveFromYear(context, request, draft);
        ResolvedValue<Integer> toYear = resolveToYear(context, request, draft);

        if (fromYear.value() < properties.getEarliestSupportedYear()) {
            throw failure("INVALID_YEAR_RANGE", "$.fromYear", "开始年份过早", true);
        }
        if (fromYear.value() > context.currentYear() || toYear.value() > context.currentYear()) {
            throw failure("FUTURE_YEAR_NOT_ALLOWED", "$", "不允许未来年份", true);
        }
        if (fromYear.value() > toYear.value()) {
            throw failure("INVALID_YEAR_RANGE", "$", "开始年份不能晚于结束年份", true);
        }
        return new ResolvedYears(fromYear, toYear);
    }

    private ResolvedValue<Integer> resolveFromYear(
            SearchPlanGenerationContext context,
            SearchRequest request,
            SearchPlanDraft draft
    ) {
        if (request.fromYear() != null) {
            return new ResolvedValue<>(request.fromYear(), ConstraintOrigin.USER_EXPLICIT);
        }
        if (draft.fromYear() != null) {
            return new ResolvedValue<>(draft.fromYear(), ConstraintOrigin.MODEL_DERIVED);
        }
        if (draft.recentYears() != null) {
            return new ResolvedValue<>(
                    context.currentYear() - draft.recentYears() + 1,
                    ConstraintOrigin.MODEL_DERIVED
            );
        }
        return new ResolvedValue<>(
                properties.getEarliestSupportedYear(),
                ConstraintOrigin.SYSTEM_DEFAULT
        );
    }

    private ResolvedValue<Integer> resolveToYear(
            SearchPlanGenerationContext context,
            SearchRequest request,
            SearchPlanDraft draft
    ) {
        if (request.toYear() != null) {
            return new ResolvedValue<>(request.toYear(), ConstraintOrigin.USER_EXPLICIT);
        }
        if (draft.toYear() != null) {
            return new ResolvedValue<>(draft.toYear(), ConstraintOrigin.MODEL_DERIVED);
        }
        ConstraintOrigin origin = draft.recentYears() == null
                ? ConstraintOrigin.SYSTEM_DEFAULT
                : ConstraintOrigin.MODEL_DERIVED;
        return new ResolvedValue<>(context.currentYear(), origin);
    }

    private ResolvedValue<Integer> resolveResultLimit(SearchRequest request, SearchPlanDraft draft) {
        ResolvedValue<Integer> resultLimit;
        if (request.limit() != null) {
            resultLimit = new ResolvedValue<>(request.limit(), ConstraintOrigin.USER_EXPLICIT);
        } else if (draft.resultLimit() != null) {
            resultLimit = new ResolvedValue<>(draft.resultLimit(), ConstraintOrigin.MODEL_DERIVED);
        } else {
            resultLimit = new ResolvedValue<>(
                    properties.getDefaultResultLimit(),
                    ConstraintOrigin.SYSTEM_DEFAULT
            );
        }
        if (resultLimit.value() < 1 || resultLimit.value() > properties.getMaxResultLimit()) {
            throw failure("INVALID_RESULT_LIMIT", "$.resultLimit", "结果数量超出范围", true);
        }
        return resultLimit;
    }

    private int calculateCandidateLimit(int resultLimit) {
        long multiplied = (long) resultLimit * properties.getCandidateMultiplier();
        return (int) Math.min(
                properties.getMaxCandidateLimit(),
                Math.max(resultLimit, multiplied)
        );
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null) {
            throw failure("MISSING_TEXT", "$." + field, field + " 不能为空", true);
        }
        String normalized = normalizeWhitespace(value);
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw failure("INVALID_TEXT_LENGTH", "$." + field, field + " 长度不合法", true);
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        return value.trim().replaceAll(" {2,}", " ");
    }

    private SearchConstraintOrigins origins(
            ResolvedYears years,
            ResolvedValue<Integer> resultLimit,
            ResolvedValue<List<String>> publicationTypes,
            ResolvedValue<SearchSort> sort
    ) {
        EnumMap<SearchConstraintField, ConstraintOrigin> origins =
                new EnumMap<>(SearchConstraintField.class);
        origins.put(SearchConstraintField.ORIGINAL_QUERY, ConstraintOrigin.USER_EXPLICIT);
        origins.put(SearchConstraintField.TOPIC, ConstraintOrigin.MODEL_DERIVED);
        origins.put(SearchConstraintField.ENGLISH_KEYWORDS, ConstraintOrigin.MODEL_DERIVED);
        origins.put(SearchConstraintField.SEARCH_QUERY, ConstraintOrigin.MODEL_DERIVED);
        origins.put(SearchConstraintField.FROM_YEAR, years.fromYear().origin());
        origins.put(SearchConstraintField.TO_YEAR, years.toYear().origin());
        origins.put(SearchConstraintField.LANGUAGES, ConstraintOrigin.MODEL_DERIVED);
        origins.put(SearchConstraintField.PUBLICATION_TYPES, publicationTypes.origin());
        origins.put(SearchConstraintField.SORT, sort.origin());
        origins.put(SearchConstraintField.RESULT_LIMIT, resultLimit.origin());
        origins.put(SearchConstraintField.CANDIDATE_LIMIT, ConstraintOrigin.SYSTEM_FIXED);
        origins.put(SearchConstraintField.MAX_SEARCH_ROUNDS, ConstraintOrigin.SYSTEM_FIXED);
        origins.put(SearchConstraintField.MAX_PLAN_ADJUSTMENTS, ConstraintOrigin.SYSTEM_FIXED);
        origins.put(SearchConstraintField.MAX_BUSINESS_STEPS, ConstraintOrigin.SYSTEM_FIXED);
        origins.put(SearchConstraintField.MAX_UNIQUE_CANDIDATES, ConstraintOrigin.SYSTEM_FIXED);
        origins.put(SearchConstraintField.MAX_CROSSREF_CALLS, ConstraintOrigin.SYSTEM_FIXED);
        origins.put(SearchConstraintField.TOTAL_TIMEOUT, ConstraintOrigin.SYSTEM_FIXED);
        return new SearchConstraintOrigins(origins);
    }

    private void validateConfiguration() {
        if (properties.getDefaultResultLimit() < 1
                || properties.getDefaultResultLimit() > properties.getMaxResultLimit()
                || properties.getMaxResultLimit() != SearchPlan.MAX_RESULT_LIMIT
                || properties.getCandidateMultiplier() < 1
                || properties.getMaxCandidateLimit() != SearchPlan.MAX_CANDIDATE_LIMIT
                || properties.getEarliestSupportedYear() != SearchPlan.EARLIEST_SUPPORTED_YEAR) {
            throw new IllegalStateException("文献检索配置与 SearchPlan 不变量不一致");
        }
    }

    private SearchPlanValidationException failure(
            String code,
            String path,
            String message,
            boolean retryable
    ) {
        return new SearchPlanValidationException(
                ValidationStage.BUSINESS_RULE,
                List.of(new ValidationIssue(code, path, message, retryable))
        );
    }

    private record ResolvedYears(
            ResolvedValue<Integer> fromYear,
            ResolvedValue<Integer> toYear
    ) {
    }
}
