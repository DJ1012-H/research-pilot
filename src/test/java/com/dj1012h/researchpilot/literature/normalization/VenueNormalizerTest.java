package com.dj1012h.researchpilot.literature.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VenueNormalizerTest {

    private final VenueNormalizer normalizer = new VenueNormalizer();

    @Test
    void shouldNormalizeFormattingButNotExpandAbbreviations() {
        assertThat(normalizer.normalize(" IEEE\nTransactions  ")).isEqualTo("ieee transactions");
        assertThat(normalizer.normalize("TGRS")).isEqualTo("tgrs");
        assertThat(normalizer.normalize("IEEE Transactions on Geoscience and Remote Sensing"))
                .isNotEqualTo(normalizer.normalize("TGRS"));
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("   ")).isNull();
    }
}
