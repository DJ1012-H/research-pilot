package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;
import com.dj1012h.researchpilot.literature.model.SearchConstraintField;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPlanProvenanceTest {

    private final SearchPlanBusinessValidator validator =
            new SearchPlanBusinessValidator(new LiteratureSearchProperties());

    @Test
    void shouldRecordRequestYearsAsUserExplicit() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", 2021, 2025, null),
                draft(null, 2020, 2024, null)
        );

        assertOrigin(result, SearchConstraintField.FROM_YEAR, ConstraintOrigin.USER_EXPLICIT);
        assertOrigin(result, SearchConstraintField.TO_YEAR, ConstraintOrigin.USER_EXPLICIT);
    }

    @Test
    void shouldRecordDraftYearsAsModelDerived() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", null, null, null),
                draft(null, 2020, 2025, null)
        );

        assertOrigin(result, SearchConstraintField.FROM_YEAR, ConstraintOrigin.MODEL_DERIVED);
        assertOrigin(result, SearchConstraintField.TO_YEAR, ConstraintOrigin.MODEL_DERIVED);
    }

    @Test
    void shouldRecordRelativeYearRangeAsModelDerived() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", null, null, null),
                draft(5, null, null, null)
        );

        assertThat(result.plan().fromYear()).isEqualTo(2022);
        assertThat(result.plan().toYear()).isEqualTo(2026);
        assertOrigin(result, SearchConstraintField.FROM_YEAR, ConstraintOrigin.MODEL_DERIVED);
        assertOrigin(result, SearchConstraintField.TO_YEAR, ConstraintOrigin.MODEL_DERIVED);
    }

    @Test
    void shouldRecordDefaultYearsAsSystemDefault() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", null, null, null),
                draft(null, null, null, null)
        );

        assertOrigin(result, SearchConstraintField.FROM_YEAR, ConstraintOrigin.SYSTEM_DEFAULT);
        assertOrigin(result, SearchConstraintField.TO_YEAR, ConstraintOrigin.SYSTEM_DEFAULT);
    }

    @Test
    void shouldRecordRequestLimitAsUserExplicit() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", null, null, 7),
                draft(null, null, null, 5)
        );

        assertOrigin(result, SearchConstraintField.RESULT_LIMIT, ConstraintOrigin.USER_EXPLICIT);
    }

    @Test
    void shouldRecordDraftResultLimitAsModelDerived() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", null, null, null),
                draft(null, null, null, 7)
        );

        assertOrigin(result, SearchConstraintField.RESULT_LIMIT, ConstraintOrigin.MODEL_DERIVED);
    }

    @Test
    void shouldRecordDefaultResultLimitAsSystemDefaultAndBudgetsAsFixed() {
        SearchPlanValidationResult result = validate(
                new SearchRequest("query", null, null, null),
                draft(null, null, null, null)
        );

        assertOrigin(result, SearchConstraintField.RESULT_LIMIT, ConstraintOrigin.SYSTEM_DEFAULT);
        assertOrigin(result, SearchConstraintField.CANDIDATE_LIMIT, ConstraintOrigin.SYSTEM_FIXED);
        assertOrigin(result, SearchConstraintField.MAX_SEARCH_ROUNDS, ConstraintOrigin.SYSTEM_FIXED);
        assertOrigin(result, SearchConstraintField.MAX_PLAN_ADJUSTMENTS, ConstraintOrigin.SYSTEM_FIXED);
        assertOrigin(result, SearchConstraintField.MAX_CROSSREF_CALLS, ConstraintOrigin.SYSTEM_FIXED);
        assertOrigin(result, SearchConstraintField.LANGUAGES, ConstraintOrigin.MODEL_DERIVED);
        assertOrigin(result, SearchConstraintField.PUBLICATION_TYPES, ConstraintOrigin.MODEL_DERIVED);
    }

    private SearchPlanValidationResult validate(SearchRequest request, SearchPlanDraft draft) {
        return validator.validateWithOrigins(context(request), draft);
    }

    private void assertOrigin(
            SearchPlanValidationResult result,
            SearchConstraintField field,
            ConstraintOrigin expected
    ) {
        assertThat(result.origins().originOf(field)).isEqualTo(expected);
    }

    private SearchPlanDraft draft(
            Integer recentYears,
            Integer fromYear,
            Integer toYear,
            Integer resultLimit
    ) {
        return new SearchPlanDraft(
                "remote sensing change detection",
                List.of("remote sensing", "change detection"),
                "remote sensing change detection",
                List.of("en"),
                List.of("article"),
                null,
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
                Instant.parse("2026-07-30T00:00:00Z"),
                2026
        );
    }
}
