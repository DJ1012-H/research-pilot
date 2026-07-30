package com.dj1012h.researchpilot.integration.cache;

import com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicQuery;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexProperties;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyFactoryTest {

    private final CacheKeyFactory keys = new CacheKeyFactory(
            new LiteratureCacheProperties(), new DoiNormalizer(), new OpenAlexProperties(), new CrossrefProperties());

    @Test
    void equivalentDoiFormsShareAnOpaqueKey() {
        String first = keys.crossrefDoi("https://doi.org/10.1000/Example").orElseThrow();
        String second = keys.crossrefDoi("doi:10.1000/example").orElseThrow();

        assertThat(first).isEqualTo(second).startsWith("research-pilot:literature:v1:crossref:doi:")
                .doesNotContain("10.1000", "example");
    }

    @Test
    void allOpenAlexRequestFieldsContributeToTheDigest() {
        OpenAlexQuery baseline = query(List.of("article"), List.of("en"), OpenAlexQuery.Sort.RELEVANCE, 10);
        assertThat(keys.openAlexSearch(baseline))
                .isNotEqualTo(keys.openAlexSearch(new OpenAlexQuery(
                        "different query", baseline.fromPublicationDate(), baseline.toPublicationDate(),
                        baseline.workTypes(), baseline.languages(), baseline.sort(), baseline.perPage())))
                .isNotEqualTo(keys.openAlexSearch(new OpenAlexQuery(
                        baseline.search(), LocalDate.of(2023, 1, 1), baseline.toPublicationDate(),
                        baseline.workTypes(), baseline.languages(), baseline.sort(), baseline.perPage())))
                .isNotEqualTo(keys.openAlexSearch(new OpenAlexQuery(
                        baseline.search(), baseline.fromPublicationDate(), LocalDate.of(2027, 1, 1),
                        baseline.workTypes(), baseline.languages(), baseline.sort(), baseline.perPage())))
                .isNotEqualTo(keys.openAlexSearch(query(List.of("review"), List.of("en"), OpenAlexQuery.Sort.RELEVANCE, 10)))
                .isNotEqualTo(keys.openAlexSearch(query(List.of("article"), List.of("zh"), OpenAlexQuery.Sort.RELEVANCE, 10)))
                .isNotEqualTo(keys.openAlexSearch(query(List.of("article"), List.of("en"), OpenAlexQuery.Sort.NEWEST, 10)))
                .isNotEqualTo(keys.openAlexSearch(query(List.of("article"), List.of("en"), OpenAlexQuery.Sort.RELEVANCE, 11)));
    }

    @Test
    void setLikeFilterOrderingIsDeterministicAndBibliographicKeyIsOpaque() {
        String first = keys.openAlexSearch(query(List.of("review", "article"), List.of("zh", "en"), OpenAlexQuery.Sort.RELEVANCE, 10));
        String second = keys.openAlexSearch(query(List.of("article", "review"), List.of("en", "zh"), OpenAlexQuery.Sort.RELEVANCE, 10));
        String bibliographic = keys.crossrefBibliographic(new CrossrefBibliographicQuery(
                "A private title", "Ada Lovelace", 2026, "Private Journal"));

        assertThat(first).isEqualTo(second);
        assertThat(bibliographic).startsWith("research-pilot:literature:v1:crossref:bibliographic:")
                .doesNotContain("private", "ada", "journal");
        assertThat(bibliographic)
                .isNotEqualTo(keys.crossrefBibliographic(new CrossrefBibliographicQuery(
                        "A different title", "Ada Lovelace", 2026, "Private Journal")))
                .isNotEqualTo(keys.crossrefBibliographic(new CrossrefBibliographicQuery(
                        "A private title", "Grace Hopper", 2026, "Private Journal")))
                .isNotEqualTo(keys.crossrefBibliographic(new CrossrefBibliographicQuery(
                        "A private title", "Ada Lovelace", 2025, "Private Journal")))
                .isNotEqualTo(keys.crossrefBibliographic(new CrossrefBibliographicQuery(
                        "A private title", "Ada Lovelace", 2026, "Other Journal")));
    }

    @Test
    void effectiveProviderPageBoundsContributeToTheKey() {
        OpenAlexProperties defaultOpenAlex = new OpenAlexProperties();
        CrossrefProperties defaultCrossref = new CrossrefProperties();
        OpenAlexProperties changedOpenAlex = new OpenAlexProperties();
        CrossrefProperties changedCrossref = new CrossrefProperties();
        changedOpenAlex.setDefaultPageSize(30);
        changedCrossref.setBibliographicRows(4);
        CacheKeyFactory first = new CacheKeyFactory(
                new LiteratureCacheProperties(), new DoiNormalizer(), defaultOpenAlex, defaultCrossref);
        CacheKeyFactory second = new CacheKeyFactory(
                new LiteratureCacheProperties(), new DoiNormalizer(), changedOpenAlex, changedCrossref);

        OpenAlexQuery defaultPageQuery = new OpenAlexQuery("private query", LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1), List.of("article"), List.of("en"), OpenAlexQuery.Sort.RELEVANCE, null);
        CrossrefBibliographicQuery bibliographic = new CrossrefBibliographicQuery("Title", "Ada", 2026, "Journal");
        assertThat(first.openAlexSearch(defaultPageQuery)).isNotEqualTo(second.openAlexSearch(defaultPageQuery));
        assertThat(first.crossrefBibliographic(bibliographic)).isNotEqualTo(second.crossrefBibliographic(bibliographic));
    }

    private OpenAlexQuery query(List<String> types, List<String> languages, OpenAlexQuery.Sort sort, int perPage) {
        return new OpenAlexQuery("private query", LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1),
                types, languages, sort, perPage);
    }
}
