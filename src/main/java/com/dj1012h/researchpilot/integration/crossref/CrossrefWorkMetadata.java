package com.dj1012h.researchpilot.integration.crossref;

import java.util.List;
import java.util.Objects;

/** Provider-independent bibliographic metadata returned by Crossref. */
public record CrossrefWorkMetadata(
        String doi,
        String title,
        List<String> authorNames,
        Integer publicationYear,
        String venue,
        String workType,
        String publisher
) {
    public CrossrefWorkMetadata {
        doi = requireText(doi, "doi");
        authorNames = List.copyOf(Objects.requireNonNull(authorNames, "authorNames 不能为空"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }
}
