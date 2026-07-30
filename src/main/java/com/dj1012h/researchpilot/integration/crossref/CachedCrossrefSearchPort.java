package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.cache.CacheKeyFactory;
import com.dj1012h.researchpilot.integration.cache.CacheValueKind;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheProperties;
import com.dj1012h.researchpilot.integration.cache.LiteratureCacheService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Optional cache-aside decorator. Cached metadata still flows through existing verification code. */
@Component
@Primary
public class CachedCrossrefSearchPort implements CrossrefSearchPort {

    private final CrossrefSearchAdapter delegate;
    private final CacheKeyFactory keyFactory;
    private final LiteratureCacheService cache;
    private final LiteratureCacheProperties properties;

    public CachedCrossrefSearchPort(
            CrossrefSearchAdapter delegate,
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
    public CrossrefLookupResult findByDoi(String doi) {
        return keyFactory.crossrefDoi(doi)
                .flatMap(key -> cache.read(key, CacheValueKind.CROSSREF_DOI, CrossrefLookupResult.class)
                        .or(() -> java.util.Optional.of(loadDoi(key, doi))))
                .orElseGet(() -> delegate.findByDoi(doi));
    }

    @Override
    public CrossrefBibliographicLookupResult findByBibliographic(CrossrefBibliographicQuery query) {
        String key = keyFactory.crossrefBibliographic(query);
        return cache.read(key, CacheValueKind.CROSSREF_BIBLIOGRAPHIC, CrossrefBibliographicLookupResult.class)
                .orElseGet(() -> loadBibliographic(key, query));
    }

    private CrossrefLookupResult loadDoi(String key, String doi) {
        CrossrefLookupResult result = delegate.findByDoi(doi);
        cache.write(key, CacheValueKind.CROSSREF_DOI, result, doiTtl(result));
        return result;
    }

    private CrossrefBibliographicLookupResult loadBibliographic(String key, CrossrefBibliographicQuery query) {
        CrossrefBibliographicLookupResult result = delegate.findByBibliographic(query);
        cache.write(key, CacheValueKind.CROSSREF_BIBLIOGRAPHIC, result, bibliographicTtl(result));
        return result;
    }

    private java.time.Duration doiTtl(CrossrefLookupResult result) {
        return result.status() == CrossrefLookupResult.Status.NOT_FOUND
                ? properties.getNotFoundTtl() : properties.getCrossrefTtl();
    }

    private java.time.Duration bibliographicTtl(CrossrefBibliographicLookupResult result) {
        return result.status() == CrossrefBibliographicLookupResult.Status.NOT_FOUND
                ? properties.getNotFoundTtl() : properties.getCrossrefTtl();
    }
}
