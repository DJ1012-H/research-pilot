package com.dj1012h.researchpilot.literature.normalization;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAlexIdNormalizerTest {

    private final OpenAlexIdNormalizer normalizer = new OpenAlexIdNormalizer();

    @ParameterizedTest
    @ValueSource(strings = {"W123456789", "w123456789", " https://openalex.org/W123456789 "})
    void shouldNormalizeWorkIdForms(String rawId) {
        assertThat(normalizer.normalize(rawId)).isEqualTo("W123456789");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-work", "https://example.org/W123", "W-123"})
    void shouldRejectNonWorkIdentifiers(String rawId) {
        assertThat(normalizer.normalize(rawId)).isNull();
    }
}
