package com.dj1012h.researchpilot.integration.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkResponse(
        String status,
        @JsonProperty("message-type") String messageType,
        @JsonProperty("message-version") String messageVersion,
        CrossrefWorkMessage message
) {
}
