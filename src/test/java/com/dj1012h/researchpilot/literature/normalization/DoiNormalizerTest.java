package com.dj1012h.researchpilot.literature.normalization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DoiNormalizerTest {

    private final DoiNormalizer normalizer = new DoiNormalizer();

    @ParameterizedTest
    @ValueSource(strings = {
            "10.1038/S41586-021-03819-2",
            "  10.1038/S41586-021-03819-2  ",
            "doi:10.1038/S41586-021-03819-2",
            "DOI: 10.1038/S41586-021-03819-2",
            "https://doi.org/10.1038/S41586-021-03819-2",
            "http://doi.org/10.1038/S41586-021-03819-2",
            "HTTPS://DX.DOI.ORG/10.1038/S41586-021-03819-2",
            "http://dx.doi.org/10.1038/S41586-021-03819-2",
            "“10.1038/S41586-021-03819-2”，",
            "10.1038/S41586-021-03819-2)"
    })
    void shouldNormalizeCommonDoiForms(String rawDoi) {
        assertThat(normalizer.normalize(rawDoi)).isEqualTo("10.1038/s41586-021-03819-2");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "doi:",
            "https://doi.org/",
            "not-a-doi",
            "10.abc/example",
            "10.1000/has space",
            "10.1000/example suffix"
    })
    void shouldRejectUnrecognizableOrInvalidDoi(String rawDoi) {
        assertThat(normalizer.normalize(rawDoi)).isNull();
    }

    @Test
    void shouldPreserveBalancedAndLegalSuffixPunctuation() {
        assertThat(normalizer.normalize("10.1002/(SICI)1234"))
                .isEqualTo("10.1002/(sici)1234");
        assertThat(normalizer.normalize("10.1000/example.;:-"))
                .isEqualTo("10.1000/example.;:-");
    }
}
