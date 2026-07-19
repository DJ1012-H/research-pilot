package com.dj1012h.researchpilot.literature.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated OpenAlex query parameters. It intentionally exposes no raw filter or URL field.
 */
public record OpenAlexQuery(
        String search,
        LocalDate fromPublicationDate,
        LocalDate toPublicationDate,
        List<String> workTypes,
        Sort sort,
        Integer perPage
) {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Pattern SAFE_WORK_TYPE = Pattern.compile("[a-z][a-z0-9-]*");

    public OpenAlexQuery {
        search = requireText(search, "search");
        fromPublicationDate = Objects.requireNonNull(fromPublicationDate, "fromPublicationDate 不能为空");
        toPublicationDate = Objects.requireNonNull(toPublicationDate, "toPublicationDate 不能为空");
        workTypes = List.copyOf(Objects.requireNonNull(workTypes, "workTypes 不能为空"));
        sort = Objects.requireNonNull(sort, "sort 不能为空");

        if (toPublicationDate.isBefore(fromPublicationDate)) {
            throw new IllegalArgumentException("toPublicationDate 不能早于 fromPublicationDate");
        }
        if (workTypes.stream().anyMatch(type ->
                type == null || !SAFE_WORK_TYPE.matcher(type).matches())) {
            throw new IllegalArgumentException("workTypes 包含不安全或无效的类型");
        }
        if (perPage != null && (perPage < 1 || perPage > MAX_PAGE_SIZE)) {
            throw new IllegalArgumentException("perPage 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
    }

    public int pageSizeOrDefault(int defaultPageSize) {
        int pageSize = perPage == null ? defaultPageSize : perPage;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("有效 perPage 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return pageSize;
    }

    public enum Sort {
        RELEVANCE("relevance_score:desc"),
        NEWEST("publication_date:desc"),
        MOST_CITED("cited_by_count:desc");

        private final String apiValue;

        Sort(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
