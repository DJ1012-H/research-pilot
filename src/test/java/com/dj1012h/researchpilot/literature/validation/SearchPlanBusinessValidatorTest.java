package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPlanBusinessValidatorTest {

    private final LiteratureSearchProperties properties = new LiteratureSearchProperties();
    private final SearchPlanBusinessValidator validator =
            new SearchPlanBusinessValidator(properties);

    @Test
    void shouldApplyRequestThenDraftThenDefaultPrecedence() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", 2023, null, 10);
        SearchPlanDraft draft = draft(
                null, 2020, 2025, 5,
                List.of("en"), List.of("article"), "newest"
        );

        SearchPlan plan = validator.validate(context(request), draft);

        assertThat(plan.originalQuery()).isEqualTo(request.query());
        assertThat(plan.fromYear()).isEqualTo(2023);
        assertThat(plan.toYear()).isEqualTo(2025);
        assertThat(plan.resultLimit()).isEqualTo(10);
        assertThat(plan.candidateLimit()).isEqualTo(30);
        assertThat(plan.sort()).isEqualTo(SearchSort.NEWEST);
    }

    @Test
    void shouldResolveRelativeYearsFromContextClock() {
        SearchPlan plan = validator.validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                draft(5, null, null, null, List.of(), List.of(), null)
        );

        assertThat(plan.fromYear()).isEqualTo(2022);
        assertThat(plan.toYear()).isEqualTo(2026);
        assertThat(plan.resultLimit()).isEqualTo(5);
        assertThat(plan.candidateLimit()).isEqualTo(15);
        assertThat(plan.sort()).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    void shouldUseFullSupportedRangeWhenNoTimeConstraintExists() {
        SearchPlan plan = validator.validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                draft(null, null, null, null, List.of(), List.of(), null)
        );

        assertThat(plan.fromYear()).isEqualTo(1900);
        assertThat(plan.toYear()).isEqualTo(2026);
    }

    @Test
    void shouldDeduplicateKeywordsAndConvertSupportedLanguages() {
        SearchPlanDraft draft = new SearchPlanDraft(
                "Mamba remote sensing change detection",
                List.of("Mamba", " mamba ", "remote   sensing"),
                "Mamba remote sensing change detection",
                List.of("en", "zh"),
                List.of("article", "review"),
                "most_cited",
                null,
                2020,
                2026,
                15
        );

        SearchPlan plan = validator.validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                draft
        );

        assertThat(plan.englishKeywords()).containsExactly("Mamba", "remote sensing");
        assertThat(plan.languages()).containsExactly(LanguageCode.EN, LanguageCode.ZH);
        assertThat(plan.sort()).isEqualTo(SearchSort.MOST_CITED);
        assertThat(plan.candidateLimit()).isEqualTo(45);
    }

    @Test
    void shouldRejectRelativeAndExplicitTimeConflict() {
        assertBusinessFailure(
                draft(5, 2020, null, null, List.of(), List.of(), null),
                "CONFLICTING_TIME_CONSTRAINTS"
        );
    }

    @Test
    void shouldRejectRequestRangeWithStartAfterEnd() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", 2025, 2022, null);

        assertThatThrownBy(() -> validator.validate(
                context(request),
                draft(null, null, null, null, List.of(), List.of(), null)
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception ->
                        assertThat(exception.getIssues())
                                .extracting(ValidationIssue::code)
                                .containsExactly("INVALID_YEAR_RANGE"));
    }

    @Test
    void shouldRejectFutureYear() {
        assertBusinessFailure(
                draft(null, 2027, null, null, List.of(), List.of(), null),
                "FUTURE_YEAR_NOT_ALLOWED"
        );
    }

    @Test
    void shouldRejectUnsupportedLanguageEvenIfSchemaIsBypassed() {
        assertBusinessFailure(
                draft(null, null, null, null, List.of("fr"), List.of(), null),
                "UNSUPPORTED_LANGUAGE"
        );
    }

    @Test
    void shouldRejectUnsupportedPublicationTypeEvenIfSchemaIsBypassed() {
        assertBusinessFailure(
                draft(null, null, null, null, List.of(), List.of("conference-paper"), null),
                "UNSUPPORTED_PUBLICATION_TYPE"
        );
    }

    @Test
    void shouldRejectUnsupportedSortEvenIfSchemaIsBypassed() {
        assertBusinessFailure(
                draft(null, null, null, null, List.of(), List.of(), "popular"),
                "INVALID_SORT"
        );
    }

    @Test
    void shouldRejectQueryThatDoesNotRelateToAnyKeyword() {
        SearchPlanDraft draft = new SearchPlanDraft(
                "Mamba remote sensing change detection",
                List.of("Mamba"),
                "transformer protein folding",
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                draft
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception ->
                        assertThat(exception.getIssues())
                                .extracting(ValidationIssue::code)
                                .containsExactly("QUERY_KEYWORD_MISMATCH"));
    }

    private void assertBusinessFailure(SearchPlanDraft draft, String expectedCode) {
        assertThatThrownBy(() -> validator.validate(
                context(new SearchRequest("Mamba 遥感变化检测", null, null, null)),
                draft
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.BUSINESS_RULE);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly(expectedCode);
                });
    }

    private SearchPlanDraft draft(
            Integer recentYears,
            Integer fromYear,
            Integer toYear,
            Integer resultLimit,
            List<String> languages,
            List<String> publicationTypes,
            String sort
    ) {
        return new SearchPlanDraft(
                "Mamba remote sensing change detection",
                List.of("Mamba", "remote sensing", "change detection"),
                "Mamba remote sensing change detection",
                languages,
                publicationTypes,
                sort,
                recentYears,
                fromYear,
                toYear,
                resultLimit
        );
    }

    private SearchPlanGenerationContext context(SearchRequest request) {
        return new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                request,
                Instant.parse("2026-07-20T08:00:00Z"),
                2026
        );
    }
}
