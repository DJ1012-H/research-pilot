package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.cache.CacheKeyFactory;
import com.dj1012h.researchpilot.integration.cache.CacheValueKind;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheProperties;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheService;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Optional, fail-open cache-aside decorator for validated OpenAlex port results. */
@Component
@Primary
public class CachedOpenAlexSearchPort implements OpenAlexSearchPort {

    private final OpenAlexSearchAdapter delegate;
    private final CacheKeyFactory keyFactory;
    private final LiteratureCacheService cache;
    private final LiteratureCacheProperties properties;

    public CachedOpenAlexSearchPort(
            OpenAlexSearchAdapter delegate,
            CacheKeyFactory keyFactory,
            LiteratureCacheService cache,
            LiteratureCacheProperties properties
    ) {
        this.delegate = delegate;
        this.keyFactory = keyFactory;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public OpenAlexSearchResult search(OpenAlexQuery query) {
        String key = keyFactory.openAlexSearch(query);
        return cache.read(key, CacheValueKind.OPENALEX_SEARCH, OpenAlexSearchResult.class)
                .orElseGet(() -> {
                    OpenAlexSearchResult result = delegate.search(query);
                    cache.write(key, CacheValueKind.OPENALEX_SEARCH, result, properties.getOpenalexTtl());
                    return result;
                });
    }
}
