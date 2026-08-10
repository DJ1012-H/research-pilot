package com.dj1012h.researchpilot.literature.rag;

import java.util.List;
import java.util.Objects;

/** Controlled, normalized paper fields and their deterministic embedding segments. */
public record RagPaperDocument(
        String doi,
        String title,
        List<String> authors,
        Integer publicationYear,
        String venue,
        String language,
        List<String> keywords,
        List<RagDocumentSegment> segments
) {

    public RagPaperDocument {
        doi = Objects.requireNonNull(doi, "doi must not be null");
        title = Objects.requireNonNull(title, "title must not be null");
        authors = List.copyOf(Objects.requireNonNull(authors, "authors must not be null"));
        venue = Objects.requireNonNull(venue, "venue must not be null");
        language = Objects.requireNonNull(language, "language must not be null");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords must not be null"));
        segments = List.copyOf(Objects.requireNonNull(segments, "segments must not be null"));
        if (segments.isEmpty() || segments.getFirst().segmentType() != RagSegmentType.METADATA) {
            throw new IllegalArgumentException("paper document must start with one metadata segment");
        }
    }
}
