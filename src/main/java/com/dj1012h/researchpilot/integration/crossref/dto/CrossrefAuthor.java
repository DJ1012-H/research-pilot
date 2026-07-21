package com.dj1012h.researchpilot.integration.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefAuthor(String given, String family, String name) {
}
