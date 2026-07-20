package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAlexQueryFactoryTest {

    private final OpenAlexQueryFactory factory = new OpenAlexQueryFactory();

    @Test
    void shouldMapDateRangeAndMultipleWorkTypes() {
        SearchPlan plan = plan(List.of(" Article ", "review", "article"), 20);

        OpenAlexQuery query = factory.create(plan);

        assertThat(query.fromPublicationDate()).isEqualTo(LocalDate.of(2022, 1, 1));
        assertThat(query.toPublicationDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(query.workTypes()).containsExactly("article", "review");
        assertThat(query.search()).isEqualTo("Mamba remote sensing change detection");
        assertThat(query.perPage()).isEqualTo(20);
        assertThat(query.languages()).containsExactly("en", "zh");
    }

    @ParameterizedTest
    @MethodSource("sortMappings")
    void shouldMapSupportedSorts(
            SearchSort requestedSort,
            OpenAlexQuery.Sort expectedSort,
            String expectedApiValue
    ) {
        OpenAlexQuery query = factory.create(plan(List.of(), 20, requestedSort));

        assertThat(query.sort()).isEqualTo(expectedSort);
        assertThat(query.sort().apiValue()).isEqualTo(expectedApiValue);
    }

    @Test
    void shouldNeverCreatePageSizeAboveOneHundred() {
        OpenAlexQuery query = factory.create(plan(List.of("article"), 100));

        assertThat(query.perPage()).isEqualTo(OpenAlexQuery.MAX_PAGE_SIZE);
        assertThatThrownBy(() -> new OpenAlexQuery(
                "query",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(),
                OpenAlexQuery.Sort.RELEVANCE,
                101
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("perPage");
    }

    @Test
    void shouldRejectUnsafeWorkTypeInsteadOfBuildingRawFilterSyntax() {
        assertThatThrownBy(() -> factory.create(plan(List.of("article,from_publication_date:1900-01-01"), 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workTypes");
    }

    @Test
    void shouldRejectUnsafeLanguageAtOpenAlexBoundary() {
        assertThatThrownBy(() -> new OpenAlexQuery(
                "query", LocalDate.of(2022, 1, 1), LocalDate.of(2026, 12, 31),
                List.of(), List.of("en-US"), OpenAlexQuery.Sort.RELEVANCE, 20
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("languages");
    }

    private SearchPlan plan(List<String> publicationTypes, int candidateLimit) {
        return plan(publicationTypes, candidateLimit, SearchSort.RELEVANCE);
    }

    private SearchPlan plan(
            List<String> publicationTypes,
            int candidateLimit,
            SearchSort searchSort
    ) {
        return new SearchPlan(
                "近五年基于 Mamba 的遥感变化检测文章",
                "remote sensing change detection with Mamba",
                List.of("Mamba", "remote sensing change detection"),
                "Mamba remote sensing change detection",
                new LinkedHashSet<>(List.of(LanguageCode.EN, LanguageCode.ZH)),
                publicationTypes,
                searchSort,
                2022,
                2026,
                candidateLimit,
                10
        );
    }

    private static Stream<Arguments> sortMappings() {
        return Stream.of(
                Arguments.of(SearchSort.RELEVANCE, OpenAlexQuery.Sort.RELEVANCE, "relevance_score:desc"),
                Arguments.of(SearchSort.NEWEST, OpenAlexQuery.Sort.NEWEST, "publication_date:desc"),
                Arguments.of(SearchSort.MOST_CITED, OpenAlexQuery.Sort.MOST_CITED, "cited_by_count:desc")
        );
    }
}
