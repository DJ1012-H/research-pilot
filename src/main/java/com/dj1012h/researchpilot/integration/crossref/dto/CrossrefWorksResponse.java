package com.dj1012h.researchpilot.integration.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorksResponse(
        String status,
        @JsonProperty("message-type") String messageType,
        @JsonProperty("message-version") String messageVersion,
        CrossrefWorksMessage message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CrossrefWorksMessage(List<CrossrefWorkMessage> items) { }
}
