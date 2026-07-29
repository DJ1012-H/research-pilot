package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Converts a trusted search plan into the limited set of OpenAlex parameters the application supports.
 */
@Component
public class OpenAlexQueryFactory {

    public OpenAlexQuery create(SearchPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        return createQuery(plan, mapSort(plan.sort()), plan.candidateLimit());
    }

    /** Creates the same trusted query while enforcing a stricter caller-owned candidate budget. */
    public OpenAlexQuery createBounded(SearchPlan plan, int maximumPageSize) {
        Objects.requireNonNull(plan, "plan must not be null");
        if (maximumPageSize < 1) {
            throw new IllegalArgumentException("maximumPageSize must be positive");
        }
        return createQuery(plan, mapSort(plan.sort()), Math.min(plan.candidateLimit(), maximumPageSize));
    }

    /**
     * Compatibility entry point. New execution paths must use {@link #create(SearchPlan)}.
     */
    @Deprecated(forRemoval = false)
    public OpenAlexQuery create(SearchPlan plan, OpenAlexQuery.Sort sort) {
        Objects.requireNonNull(plan, "plan 不能为空");
        return createQuery(
                plan,
                Objects.requireNonNull(sort, "sort 不能为空"),
                plan.candidateLimit()
        );
    }

    private OpenAlexQuery createQuery(SearchPlan plan, OpenAlexQuery.Sort sort, int pageSize) {
        List<String> workTypes = plan.publicationTypes().stream()
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> languages = plan.languages().stream()
                .map(language -> language.apiValue().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        return new OpenAlexQuery(
                plan.searchQuery(),
                LocalDate.of(plan.fromYear(), 1, 1),
                LocalDate.of(plan.toYear(), 12, 31),
                workTypes,
                languages,
                sort,
                Math.min(pageSize, OpenAlexQuery.MAX_PAGE_SIZE)
        );
    }

    private OpenAlexQuery.Sort mapSort(SearchSort sort) {
        return switch (Objects.requireNonNull(sort, "sort 不能为空")) {
            case RELEVANCE -> OpenAlexQuery.Sort.RELEVANCE;
            case NEWEST -> OpenAlexQuery.Sort.NEWEST;
            case MOST_CITED -> OpenAlexQuery.Sort.MOST_CITED;
        };
    }
}
