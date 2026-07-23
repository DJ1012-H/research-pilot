package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/** A deterministic, bounded Crossref bibliographic query assembled from candidate metadata. */
public record CrossrefBibliographicQuery(
        String title,
        String firstAuthor,
        Integer publicationYear,
        String sourceName
) {
    public CrossrefBibliographicQuery {
        title = required(title, "title");
        firstAuthor = optional(firstAuthor);
        sourceName = optional(sourceName);
    }

    public String queryText() {
        return String.join(" ", java.util.stream.Stream.of(title, firstAuthor,
                        publicationYear == null ? null : publicationYear.toString(), sourceName)
                .filter(Objects::nonNull)
                .toList());
    }

    /** Stable comparison key for suppressing duplicate outbound bibliographic requests. */
    public String deduplicationKey() {
        return queryText().toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String name) {
        String normalized = optional(value);
        if (normalized == null) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private static String optional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
