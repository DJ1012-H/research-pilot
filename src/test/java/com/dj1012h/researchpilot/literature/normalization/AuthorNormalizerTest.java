package com.dj1012h.researchpilot.literature.normalization;

import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorNormalizerTest {

    private final AuthorNormalizer normalizer = new AuthorNormalizer();

    @Test
    void shouldNormalizeNamesWithoutInferringIdentity() {
        assertThat(normalizer.normalize(" John  A. Smith ")).isEqualTo("john a. smith");
        assertThat(normalizer.normalize("Smith, John")).isEqualTo("smith, john");
        assertThat(normalizer.normalize("John Smith")).isNotEqualTo(normalizer.normalize("Smith, John"));
        assertThat(normalizer.normalize("J. Smith")).isNotEqualTo(normalizer.normalize("John Smith"));
    }

    @Test
    void shouldExtractOnlyTheFirstUsableAuthor() {
        List<CandidatePaper.Author> authors = List.of(
                new CandidatePaper.Author("A1", "  张 三 ", null),
                new CandidatePaper.Author("A2", "Second Author", null)
        );

        assertThat(normalizer.normalizeFirstAuthor(authors)).isEqualTo("张 三");
        assertThat(normalizer.normalizeFirstAuthor(List.of())).isNull();
        assertThat(normalizer.normalizeFirstAuthor(null)).isNull();
    }
}
