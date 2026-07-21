package com.dj1012h.researchpilot.integration.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefDate(@JsonProperty("date-parts") List<List<Integer>> dateParts) {
}
