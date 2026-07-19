package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
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
    }

    @ParameterizedTest
    @MethodSource("sortMappings")
    void shouldMapSupportedSorts(OpenAlexQuery.Sort sort, String expectedApiValue) {
        OpenAlexQuery query = factory.create(plan(List.of(), 20), sort);

        assertThat(query.sort()).isEqualTo(sort);
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

    private SearchPlan plan(List<String> publicationTypes, int candidateLimit) {
        return new SearchPlan(
                "近五年基于 Mamba 的遥感变化检测文章",
                "remote sensing change detection with Mamba",
                List.of("Mamba", "remote sensing change detection"),
                "Mamba remote sensing change detection",
                List.of(),
                publicationTypes,
                2022,
                2026,
                candidateLimit,
                10
        );
    }

    private static Stream<Arguments> sortMappings() {
        return Stream.of(
                Arguments.of(OpenAlexQuery.Sort.RELEVANCE, "relevance_score:desc"),
                Arguments.of(OpenAlexQuery.Sort.NEWEST, "publication_date:desc"),
                Arguments.of(OpenAlexQuery.Sort.MOST_CITED, "cited_by_count:desc")
        );
    }
}
