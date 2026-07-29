package com.dj1012h.researchpilot.literature.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Objects;

/**
 * Source-independent paper candidate used after mapping an OpenAlex response.
 *
 * <p>External API response classes must not leak into this model. DOI
 * normalization and abstract reconstruction are responsibilities of the
 * future OpenAlex mapper.</p>
 */
public record PaperDTO(
        String openAlexId,
        String doi,
        String title,
        List<Author> authors,
        Integer publicationYear,
        String venue,
        List<String> issns,
        String publicationType,
        String landingPageUrl,
        @JsonIgnore
        String abstractText,
        String language,
        List<String> keywords,
        int citedByCount,
        LiteratureSource source
) {

    public PaperDTO {
        title = requireText(title, "title");
        authors = List.copyOf(Objects.requireNonNull(authors, "authors 不能为空"));
        issns = List.copyOf(Objects.requireNonNull(issns, "issns 不能为空"));
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords 不能为空"));
        source = Objects.requireNonNull(source, "source 不能为空");

        if (citedByCount < 0) {
            throw new IllegalArgumentException("citedByCount 不能小于 0");
        }
    }

    public record Author(
            String openAlexAuthorId,
            String displayName,
            String orcid
    ) {
        public Author {
            displayName = requireText(displayName, "displayName");
        }
    }

    public enum LiteratureSource {
        OPENALEX
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
