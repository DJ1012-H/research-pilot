package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable metadata shared by initial search-plan generation and its optional retry.
 */
public record SearchPlanGenerationContext(
        UUID requestId,
        SearchRequest request,
        Instant startedAt,
        int currentYear
) {

    public SearchPlanGenerationContext {
        requestId = Objects.requireNonNull(requestId, "requestId 不能为空");
        request = Objects.requireNonNull(request, "request 不能为空");
        startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
        if (currentYear < 1900) {
            throw new IllegalArgumentException("currentYear 不能早于 1900");
        }
    }

    public static SearchPlanGenerationContext create(SearchRequest request, Clock clock) {
        Objects.requireNonNull(clock, "clock 不能为空");
        return new SearchPlanGenerationContext(
                UUID.randomUUID(),
                request,
                Instant.now(clock),
                Year.now(clock).getValue()
        );
    }
}
