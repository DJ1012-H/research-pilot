package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.cache.CacheKeyFactory;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheProperties;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheService;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheServiceTest;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexProperties;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedCrossrefSearchPortTest {

    @Test
    void foundDoiIsCachedByNormalizedKeyWithTheNormalTtl() {
        Fixture fixture = fixture();
        CrossrefLookupResult expected = CrossrefLookupResult.found(metadata());
        when(fixture.delegate.findByDoi("https://doi.org/10.1000/example")).thenReturn(expected);

        assertThat(fixture.port.findByDoi("https://doi.org/10.1000/example")).isEqualTo(expected);
        assertThat(fixture.port.findByDoi("doi:10.1000/EXAMPLE")).isEqualTo(expected);

        verify(fixture.delegate, times(1)).findByDoi("https://doi.org/10.1000/example");
        assertThat(fixture.store.ttls.values()).containsExactly(fixture.properties.getCrossrefTtl());
    }

    @Test
    void explicitNotFoundUsesShortTtlAndIsReused() {
        Fixture fixture = fixture();
        when(fixture.delegate.findByDoi("10.1000/missing")).thenReturn(CrossrefLookupResult.notFound());

        assertThat(fixture.port.findByDoi("10.1000/missing").status()).isEqualTo(CrossrefLookupResult.Status.NOT_FOUND);
        assertThat(fixture.port.findByDoi("doi:10.1000/MISSING").status())
                .isEqualTo(CrossrefLookupResult.Status.NOT_FOUND);
        assertThat(fixture.store.ttls.values()).containsExactly(fixture.properties.getNotFoundTtl());
        verify(fixture.delegate).findByDoi("10.1000/missing");
    }

    @Test
    void bibliographicFoundAndNotFoundPreserveTheirExistingResultSemantics() {
        Fixture fixture = fixture();
        CrossrefBibliographicQuery foundQuery = new CrossrefBibliographicQuery("Title", "Ada", 2026, "Journal");
        CrossrefBibliographicQuery missingQuery = new CrossrefBibliographicQuery("Missing", null, 2026, null);
        when(fixture.delegate.findByBibliographic(foundQuery))
                .thenReturn(CrossrefBibliographicLookupResult.found(List.of(metadata())));
        when(fixture.delegate.findByBibliographic(missingQuery))
                .thenReturn(CrossrefBibliographicLookupResult.notFound());

        assertThat(fixture.port.findByBibliographic(foundQuery).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.FOUND_SINGLE);
        assertThat(fixture.port.findByBibliographic(missingQuery).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.NOT_FOUND);
        assertThat(fixture.store.ttls.values())
                .contains(fixture.properties.getCrossrefTtl(), fixture.properties.getNotFoundTtl());
    }

    @Test
    void bibliographicMultipleResultsRemainAmbiguousOnCacheHit() {
        Fixture fixture = fixture();
        CrossrefBibliographicQuery query = new CrossrefBibliographicQuery("Title", "Ada", 2026, "Journal");
        CrossrefBibliographicLookupResult expected = CrossrefBibliographicLookupResult.found(List.of(
                metadata(),
                new CrossrefWorkMetadata("10.1000/other", "Other", List.of("Grace"), 2026,
                        "Journal", "article", null)));
        when(fixture.delegate.findByBibliographic(query)).thenReturn(expected);

        assertThat(fixture.port.findByBibliographic(query).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.FOUND_MULTIPLE);
        assertThat(fixture.port.findByBibliographic(query).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.FOUND_MULTIPLE);

        verify(fixture.delegate).findByBibliographic(query);
    }

    @Test
    void providerFailureIsPropagatedAndNeverCached() {
        Fixture fixture = fixture();
        IllegalStateException failure = new IllegalStateException("rate limited");
        when(fixture.delegate.findByDoi("10.1000/failure")).thenThrow(failure);

        assertThatThrownBy(() -> fixture.port.findByDoi("10.1000/failure")).isSameAs(failure);
        assertThat(fixture.store.values).isEmpty();
        assertThat(fixture.store.writeCount).isZero();
    }

    @Test
    void redisWriteFailureDoesNotChangeCrossrefSuccess() {
        Fixture fixture = fixture();
        fixture.store.failWrites = true;
        CrossrefLookupResult expected = CrossrefLookupResult.found(metadata());
        when(fixture.delegate.findByDoi("10.1000/example")).thenReturn(expected);

        assertThat(fixture.port.findByDoi("10.1000/example")).isEqualTo(expected);
        assertThat(fixture.store.values).isEmpty();
    }

    private Fixture fixture() {
        LiteratureCacheProperties properties = new LiteratureCacheProperties();
        properties.setEnabled(true);
        LiteratureCacheServiceTest.RecordingStore store = new LiteratureCacheServiceTest.RecordingStore();
        LiteratureCacheService cache = LiteratureCacheServiceTest.cache(store, new LiteratureCacheServiceTest.MutableClock());
        CrossrefSearchAdapter delegate = mock(CrossrefSearchAdapter.class);
        CachedCrossrefSearchPort port = new CachedCrossrefSearchPort(
                delegate, new CacheKeyFactory(
                        properties, new DoiNormalizer(), new OpenAlexProperties(), new CrossrefProperties()), cache, properties);
        return new Fixture(properties, store, delegate, port);
    }

    private CrossrefWorkMetadata metadata() {
        return new CrossrefWorkMetadata("10.1000/example", "Title", List.of("Ada"), 2026,
                "Journal", "article", null);
    }

    private record Fixture(
            LiteratureCacheProperties properties,
            LiteratureCacheServiceTest.RecordingStore store,
            CrossrefSearchAdapter delegate,
            CachedCrossrefSearchPort port
    ) { }
}
