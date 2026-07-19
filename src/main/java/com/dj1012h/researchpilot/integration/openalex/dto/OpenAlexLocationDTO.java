package com.dj1012h.researchpilot.integration.openalex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAlexLocationDTO(
        @JsonProperty("landing_page_url")
        String landingPageUrl,
        @JsonProperty("pdf_url")
        String pdfUrl,
        @JsonProperty("is_oa")
        Boolean openAccess,
        Source source
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            @JsonProperty("display_name")
            String displayName
    ) {
    }
}
