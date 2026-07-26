package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BibliographicSimilarityCalculatorTest {

    private final BibliographicSimilarityCalculator calculator = new BibliographicSimilarityCalculator(
            new TitleNormalizer(), new VenueNormalizer());

    @Test
    void shouldTreatExistingUnicodeAndWhitespaceNormalizationAsExactTitleMatch() {
        assertThat(calculator.titleSimilarity(" Rank‐based  detection ", "rank-based detection"))
                .isEqualTo(1.0);
    }

    @Test
    void shouldUseBoundedDeterministicScoresForNonExactText() {
        double first = calculator.titleSimilarity("Deep learning for change detection", "Deep learning for scene change detection");
        double second = calculator.titleSimilarity("Deep learning for change detection", "Deep learning for scene change detection");

        assertThat(first).isEqualTo(second).isBetween(0.0, 1.0).isLessThan(1.0);
        assertThat(calculator.venueSimilarity("Journal of Testing", "journal of testing"))
                .isEqualTo(1.0);
    }

    @Test
    void shouldRejectMissingValuesInsteadOfConvertingThemToZero() {
        assertThatThrownBy(() -> calculator.titleSimilarity(null, "Title"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.venueSimilarity("Venue", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
