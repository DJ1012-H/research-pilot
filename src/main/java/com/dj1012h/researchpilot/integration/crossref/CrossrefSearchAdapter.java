package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.observability.LiteratureObservationMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** Adapts Crossref calls to the internal search-port model without exposing provider DTOs. */
@Component
public class CrossrefSearchAdapter implements CrossrefSearchPort {

    private final CrossrefClient client;
    private final CrossrefPaperMapper paperMapper;
    private final LiteratureObservationMetrics metrics;

    public CrossrefSearchAdapter(CrossrefClient client, CrossrefPaperMapper paperMapper) {
        this(client, paperMapper, LiteratureObservationMetrics.noop());
    }

    @Autowired
    public CrossrefSearchAdapter(
            CrossrefClient client,
            CrossrefPaperMapper paperMapper,
            LiteratureObservationMetrics metrics
    ) {
        this.client = client;
        this.paperMapper = paperMapper;
        this.metrics = metrics;
    }

    @Override
    public CrossrefLookupResult findByDoi(String doi) {
        long startedAt = System.nanoTime();
        try {
            CrossrefLookupResult result = CrossrefLookupResult.found(paperMapper.map(client.getWorkByDoi(doi).message()));
            metrics.recordProvider("crossref", "doi_lookup", "succeeded", elapsed(startedAt));
            return result;
        } catch (CrossrefApiException exception) {
            if (exception.getFailureType() == CrossrefFailureType.NOT_FOUND) {
                metrics.recordProvider("crossref", "doi_lookup", "succeeded", elapsed(startedAt));
                return CrossrefLookupResult.notFound();
            }
            metrics.recordProvider("crossref", "doi_lookup", "failed", elapsed(startedAt));
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordProvider("crossref", "doi_lookup", "failed", elapsed(startedAt));
            throw exception;
        }
    }

    @Override
    public CrossrefBibliographicLookupResult findByBibliographic(CrossrefBibliographicQuery query) {
        long startedAt = System.nanoTime();
        try {
            List<CrossrefWorkMetadata> candidates = client.getWorksByBibliographic(query).message().items().stream()
                    .map(paperMapper::map)
                    .toList();
            CrossrefBibliographicLookupResult result = candidates.isEmpty()
                    ? CrossrefBibliographicLookupResult.notFound()
                    : CrossrefBibliographicLookupResult.found(candidates);
            metrics.recordProvider("crossref", "bibliographic_lookup", "succeeded", elapsed(startedAt));
            return result;
        } catch (CrossrefApiException exception) {
            if (exception.getFailureType() == CrossrefFailureType.NOT_FOUND) {
                metrics.recordProvider("crossref", "bibliographic_lookup", "succeeded", elapsed(startedAt));
                return CrossrefBibliographicLookupResult.notFound();
            }
            metrics.recordProvider("crossref", "bibliographic_lookup", "failed", elapsed(startedAt));
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordProvider("crossref", "bibliographic_lookup", "failed", elapsed(startedAt));
            throw exception;
        }
    }

    private java.time.Duration elapsed(long startedAt) {
        return java.time.Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt));
    }

}
