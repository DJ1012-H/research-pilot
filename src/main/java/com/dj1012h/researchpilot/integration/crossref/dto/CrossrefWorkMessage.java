package com.dj1012h.researchpilot.integration.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkMessage(
        @JsonProperty("DOI") String doi,
        List<String> title,
        List<CrossrefAuthor> author,
        CrossrefDate published,
        @JsonProperty("published-online") CrossrefDate publishedOnline,
        @JsonProperty("published-print") CrossrefDate publishedPrint,
        CrossrefDate issued,
        CrossrefDate created,
        @JsonProperty("container-title") List<String> containerTitle,
        String type,
        String publisher,
        @JsonProperty("abstract") String abstractText
) {
    public CrossrefWorkMessage(
            String doi,
            List<String> title,
            List<CrossrefAuthor> author,
            CrossrefDate published,
            CrossrefDate publishedOnline,
            CrossrefDate publishedPrint,
            CrossrefDate issued,
            List<String> containerTitle,
            String type,
            String publisher
    ) {
        this(doi, title, author, published, publishedOnline, publishedPrint, issued, null,
                containerTitle, type, publisher, null);
    }

    public CrossrefWorkMessage(
            String doi,
            List<String> title,
            List<CrossrefAuthor> author,
            CrossrefDate published,
            CrossrefDate publishedOnline,
            CrossrefDate publishedPrint,
            CrossrefDate issued,
            CrossrefDate created,
            List<String> containerTitle,
            String type,
            String publisher
    ) {
        this(doi, title, author, published, publishedOnline, publishedPrint, issued, created,
                containerTitle, type, publisher, null);
    }
}
