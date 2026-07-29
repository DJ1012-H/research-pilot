package com.dj1012h.researchpilot.literature.review;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewInputTest {

    @Test
    void shouldRejectDuplicateIdsAndDoisAndExposeImmutableCollections() {
        EvidencePaper first = paper(1, "10.1000/a", "abstract A");
        EvidencePaper duplicateId = paper(1, "10.1000/b", "abstract B");
        EvidencePaper duplicateDoi = paper(2, "10.1000/a", "abstract C");

        assertThatThrownBy(() -> new ReviewInput(5, 3, 2, List.of(first, duplicateId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate citationId");
        assertThatThrownBy(() -> new ReviewInput(5, 3, 2, List.of(first, duplicateDoi)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate normalizedDoi");
        assertThatThrownBy(() -> new ReviewInput(5, 3, 1, List.of(first)).evidencePapers().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectBlankRequiredEvidenceFieldsAndMismatchedCounts() {
        assertThatThrownBy(() -> new CitationId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paper(1, "", "abstract"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paper(1, "10.1000/a", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewInput(5, 1, 0, List.of(paper(1, "10.1000/a", "abstract"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abstractEvidenceCount");
    }

    @Test
    void shouldCopyNestedAuthorNames() {
        List<String> authors = new ArrayList<>(List.of("Author"));
        EvidencePaper paper = new EvidencePaper(
                new CitationId(1), "10.1000/a", "Title", authors, 2025, "Venue", "Abstract"
        );
        authors.add("Later mutation");

        assertThatThrownBy(() -> paper.authorDisplayNames().add("mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private EvidencePaper paper(int position, String doi, String abstractText) {
        return new EvidencePaper(
                new CitationId(position), doi, "Title", List.of("Author"), 2025, "Venue", abstractText
        );
    }
}
