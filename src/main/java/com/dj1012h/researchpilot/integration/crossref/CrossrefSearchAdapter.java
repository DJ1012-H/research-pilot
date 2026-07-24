package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.stereotype.Component;

import java.util.List;

/** Adapts Crossref calls to the internal search-port model without exposing provider DTOs. */
@Component
public class CrossrefSearchAdapter implements CrossrefSearchPort {

    private final CrossrefClient client;
    private final CrossrefPaperMapper paperMapper;

    public CrossrefSearchAdapter(CrossrefClient client, CrossrefPaperMapper paperMapper) {
        this.client = client;
        this.paperMapper = paperMapper;
    }

    @Override
    public CrossrefLookupResult findByDoi(String doi) {
        try {
            return CrossrefLookupResult.found(paperMapper.map(client.getWorkByDoi(doi).message()));
        } catch (CrossrefApiException exception) {
            if (exception.getFailureType() == CrossrefFailureType.NOT_FOUND) {
                return CrossrefLookupResult.notFound();
            }
            throw exception;
        }
    }

    @Override
    public CrossrefBibliographicLookupResult findByBibliographic(CrossrefBibliographicQuery query) {
        try {
            List<CrossrefWorkMetadata> candidates = client.getWorksByBibliographic(query).message().items().stream()
                    .map(paperMapper::map)
                    .toList();
            return candidates.isEmpty()
                    ? CrossrefBibliographicLookupResult.notFound()
                    : CrossrefBibliographicLookupResult.found(candidates);
        } catch (CrossrefApiException exception) {
            if (exception.getFailureType() == CrossrefFailureType.NOT_FOUND) {
                return CrossrefBibliographicLookupResult.notFound();
            }
            throw exception;
        }
    }

}
