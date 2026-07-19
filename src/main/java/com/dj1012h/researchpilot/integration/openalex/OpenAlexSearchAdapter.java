package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps the application port independent from OpenAlex response DTOs.
 */
@Component
public class OpenAlexSearchAdapter implements OpenAlexSearchPort {

    private final OpenAlexClient client;
    private final OpenAlexPaperMapper mapper;

    public OpenAlexSearchAdapter(OpenAlexClient client, OpenAlexPaperMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public OpenAlexSearchResult search(OpenAlexQuery query) {
        OpenAlexWorksResponse response = client.search(query);
        List<CandidatePaper> candidates = mapper.map(response);
        long totalMatches = response.meta() == null || response.meta().count() == null
                ? 0
                : Math.max(0, response.meta().count());
        String nextCursor = response.meta() == null ? null : response.meta().nextCursor();
        return new OpenAlexSearchResult(totalMatches, candidates, nextCursor);
    }
}
