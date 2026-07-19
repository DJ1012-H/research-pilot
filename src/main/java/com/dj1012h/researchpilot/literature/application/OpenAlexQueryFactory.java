package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
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
        return create(plan, OpenAlexQuery.Sort.RELEVANCE);
    }

    public OpenAlexQuery create(SearchPlan plan, OpenAlexQuery.Sort sort) {
        Objects.requireNonNull(plan, "plan 不能为空");

        List<String> workTypes = plan.publicationTypes().stream()
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        return new OpenAlexQuery(
                plan.searchQuery(),
                LocalDate.of(plan.fromYear(), 1, 1),
                LocalDate.of(plan.toYear(), 12, 31),
                workTypes,
                Objects.requireNonNull(sort, "sort 不能为空"),
                Math.min(plan.candidateLimit(), OpenAlexQuery.MAX_PAGE_SIZE)
        );
    }
}
