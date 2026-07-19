package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;

public interface OpenAlexSearchPort {

    OpenAlexSearchResult search(OpenAlexQuery query);
}
