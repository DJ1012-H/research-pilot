package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.cache.CacheKeyFactory;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheProperties;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheService;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheServiceTest;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedOpenAlexSearchPortTest {

    @Test
    void cacheHitAvoidsASecondAdapterCallAndSuccessfulEmptyResultsAreCacheable() {
        LiteratureCacheProperties properties = enabledProperties();
        LiteratureCacheServiceTest.RecordingStore store = new LiteratureCacheServiceTest.RecordingStore();
        OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
        OpenAlexQuery query = query();
        OpenAlexSearchResult expected = new OpenAlexSearchResult(0, List.of(), null);
        when(delegate.search(query)).thenReturn(expected);
        CachedOpenAlexSearchPort port = port(delegate, properties, store);

        assertThat(port.search(query)).isEqualTo(expected);
        assertThat(port.search(query)).isEqualTo(expected);
        verify(delegate, times(1)).search(query);
        assertThat(store.values).hasSize(1);
    }

    @Test
    void redisReadFailureFallsBackToAdapterWithoutRepeatedRedisWaitsDuringCooldown() {
        LiteratureCacheProperties properties = enabledProperties();
        LiteratureCacheServiceTest.RecordingStore store = new LiteratureCacheServiceTest.RecordingStore();
        store.failReads = true;
        OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
        OpenAlexQuery query = query();
        when(delegate.search(query)).thenReturn(new OpenAlexSearchResult(0, List.of(), null));
        CachedOpenAlexSearchPort port = port(delegate, properties, store);

        port.search(query);
        port.search(query);

        verify(delegate, times(2)).search(query);
        assertThat(store.readCount).isEqualTo(1);
    }

    @Test
    void corruptEntryIsEvictedAndReplacedFromTheAdapter() {
        LiteratureCacheProperties properties = enabledProperties();
        LiteratureCacheServiceTest.RecordingStore store = new LiteratureCacheServiceTest.RecordingStore();
        OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
        OpenAlexQuery query = query();
        OpenAlexSearchResult expected = new OpenAlexSearchResult(0, List.of(), null);
        CacheKeyFactory keys = keys(properties);
        String key = keys.openAlexSearch(query);
        store.values.put(key, "not-json");
        when(delegate.search(query)).thenReturn(expected);
        CachedOpenAlexSearchPort port = port(delegate, properties, store);

        assertThat(port.search(query)).isEqualTo(expected);
        assertThat(store.deleted).containsExactly(key);
        assertThat(store.values).containsKey(key);
        verify(delegate).search(query);
    }

    @Test
    void adapterFailureIsPropagatedAndNeverCached() {
        LiteratureCacheProperties properties = enabledProperties();
        LiteratureCacheServiceTest.RecordingStore store = new LiteratureCacheServiceTest.RecordingStore();
        OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
        OpenAlexQuery query = query();
        IllegalStateException failure = new IllegalStateException("provider failure");
        when(delegate.search(query)).thenThrow(failure);
        CachedOpenAlexSearchPort port = port(delegate, properties, store);

        assertThatThrownBy(() -> port.search(query)).isSameAs(failure);
        assertThat(store.values).isEmpty();
        assertThat(store.writeCount).isZero();
    }

    @Test
    void redisWriteFailureStillReturnsTheAdapterResult() {
        LiteratureCacheProperties properties = enabledProperties();
        LiteratureCacheServiceTest.RecordingStore store = new LiteratureCacheServiceTest.RecordingStore();
        store.failWrites = true;
        OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
        OpenAlexQuery query = query();
        OpenAlexSearchResult expected = new OpenAlexSearchResult(0, List.of(), null);
        when(delegate.search(query)).thenReturn(expected);
        CachedOpenAlexSearchPort port = port(delegate, properties, store);

        assertThat(port.search(query)).isEqualTo(expected);
        assertThat(store.values).isEmpty();
        verify(delegate).search(query);
    }

    private CachedOpenAlexSearchPort port(
            OpenAlexSearchAdapter delegate,
            LiteratureCacheProperties properties,
            LiteratureCacheServiceTest.RecordingStore store
    ) {
        CacheKeyFactory keys = keys(properties);
        LiteratureCacheService cache = LiteratureCacheServiceTest.cache(store, new LiteratureCacheServiceTest.MutableClock());
        return new CachedOpenAlexSearchPort(delegate, keys, cache, properties);
    }

    private CacheKeyFactory keys(LiteratureCacheProperties properties) {
        return new CacheKeyFactory(
                properties, new DoiNormalizer(), new OpenAlexProperties(), new CrossrefProperties());
    }

    private LiteratureCacheProperties enabledProperties() {
        LiteratureCacheProperties properties = new LiteratureCacheProperties();
        properties.setEnabled(true);
        return properties;
    }

    private OpenAlexQuery query() {
        return new OpenAlexQuery("test", LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1),
                List.of("article"), List.of("en"), OpenAlexQuery.Sort.RELEVANCE, 10);
    }
}
