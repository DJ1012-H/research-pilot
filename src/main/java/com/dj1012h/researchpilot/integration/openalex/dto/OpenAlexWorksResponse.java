package com.dj1012h.researchpilot.integration.openalex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAlexWorksResponse(
        Meta meta,
        List<OpenAlexWorkDTO> results
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            Long count,
            @JsonProperty("next_cursor")
            String nextCursor
    ) {
    }
}
