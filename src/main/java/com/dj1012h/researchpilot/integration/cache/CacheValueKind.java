package com.dj1012h.researchpilot.integration.cache;

public enum CacheValueKind {
    OPENALEX_SEARCH("openalex", "search"),
    CROSSREF_DOI("crossref", "doi"),
    CROSSREF_BIBLIOGRAPHIC("crossref", "bibliographic");

    private final String provider;
    private final String operation;

    CacheValueKind(String provider, String operation) {
        this.provider = provider;
        this.operation = operation;
    }

    public String provider() {
        return provider;
    }

    public String operation() {
        return operation;
    }
}
