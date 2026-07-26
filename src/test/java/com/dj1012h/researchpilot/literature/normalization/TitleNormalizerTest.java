package com.dj1012h.researchpilot.literature.normalization;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TitleNormalizerTest {

    private final TitleNormalizer normalizer = new TitleNormalizer();

    @ParameterizedTest
    @MethodSource("equivalentTitles")
    void shouldNormalizeEquivalentTitleFormatting(String first, String second) {
        assertThat(normalizer.normalize(first)).isEqualTo(normalizer.normalize(second));
    }

    @ParameterizedTest
    @MethodSource("invalidTitles")
    void shouldRejectMissingOrNonBibliographicTitles(String title) {
        assertThat(normalizer.normalize(title)).isNull();
    }

    @org.junit.jupiter.api.Test
    void shouldBeIdempotentWithoutRemovingDistinguishingNumbers() {
        String normalized = normalizer.normalize(" Mamba—Remote  Sensing 2 ");

        assertThat(normalizer.normalize(normalized)).isEqualTo(normalized);
        assertThat(normalized).contains("2");
    }

    private static Stream<Arguments> equivalentTitles() {
        return Stream.of(
                Arguments.of(" Mamba—Remote\nSensing ", "mamba-remote sensing"),
                Arguments.of("Ｍａｍｂａ：Ｒｅｍｏｔｅ", "mamba:remote"),
                Arguments.of("A “Robust” Method", "a \"robust\" method"),
                Arguments.of("A…Method", "a...method")
        );
    }

    private static Stream<String> invalidTitles() {
        return Stream.of(null, "", " \n\t ", "---", "……");
    }
}
