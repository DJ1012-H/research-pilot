package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.observability.LiteratureObservationMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps the application port independent from OpenAlex response DTOs.
 */
@Component
public class OpenAlexSearchAdapter implements OpenAlexSearchPort {

    private final OpenAlexClient client;
    private final OpenAlexPaperMapper mapper;
    private final LiteratureObservationMetrics metrics;

    public OpenAlexSearchAdapter(OpenAlexClient client, OpenAlexPaperMapper mapper) {
        this(client, mapper, LiteratureObservationMetrics.noop());
    }

    @Autowired
    public OpenAlexSearchAdapter(
            OpenAlexClient client,
            OpenAlexPaperMapper mapper,
            LiteratureObservationMetrics metrics
    ) {
        this.client = client;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Override
    public OpenAlexSearchResult search(OpenAlexQuery query) {
        long startedAt = System.nanoTime();
        try {
            OpenAlexWorksResponse response = client.search(query);
            List<CandidatePaper> candidates = mapper.map(response);
            long totalMatches = response.meta() == null || response.meta().count() == null
                    ? 0
                    : Math.max(0, response.meta().count());
            String nextCursor = response.meta() == null ? null : response.meta().nextCursor();
            metrics.recordProvider("openalex", "search", "succeeded", elapsed(startedAt));
            return new OpenAlexSearchResult(totalMatches, candidates, nextCursor);
        } catch (RuntimeException exception) {
            metrics.recordProvider("openalex", "search", "failed", elapsed(startedAt));
            throw exception;
        }
    }

    private java.time.Duration elapsed(long startedAt) {
        return java.time.Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt));
    }
}
