package com.dj1012h.researchpilot.literature.model;

/**
 * Language filters supported by the first literature-search workflow.
 */
public enum LanguageCode {
    EN("en"),
    ZH("zh");

    private final String apiValue;

    LanguageCode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
