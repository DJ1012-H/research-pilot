package com.dj1012h.researchpilot.integration.openalex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAlexAuthorshipDTO(
        Author author
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(
            String id,
            @JsonProperty("display_name")
            String displayName,
            String orcid
    ) {
    }
}
