package com.dj1012h.researchpilot.integration.crossref;

public interface CrossrefSearchPort {
    CrossrefLookupResult findByDoi(String doi);

    CrossrefBibliographicLookupResult findByBibliographic(CrossrefBibliographicQuery query);
}
