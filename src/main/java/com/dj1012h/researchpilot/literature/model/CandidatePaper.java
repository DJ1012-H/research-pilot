package com.dj1012h.researchpilot.literature.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Retrieval-stage paper candidate before deduplication and external verification.
 */
public record CandidatePaper(
        String openAlexId,
        String doi,
        String title,
        List<Author> authors,
        String sourceName,
        LocalDate publicationDate,
        Integer publicationYear,
        String workType,
        int citedByCount,
        String abstractText,
        String landingPageUrl,
        String pdfUrl,
        boolean openAccess,
        CandidateSource candidateSource
) {

    public CandidatePaper {
        authors = authors == null ? List.of() : List.copyOf(authors);
        candidateSource = Objects.requireNonNull(candidateSource, "candidateSource 不能为空");
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
            Objects.requireNonNull(displayName, "displayName 不能为空");
            if (displayName.isBlank()) {
                throw new IllegalArgumentException("displayName 不能为空");
            }
        }
    }

    public enum CandidateSource {
        OPENALEX
    }
}
