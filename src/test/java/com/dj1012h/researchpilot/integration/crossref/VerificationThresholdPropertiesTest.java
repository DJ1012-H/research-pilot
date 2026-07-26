package com.dj1012h.researchpilot.integration.crossref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationThresholdPropertiesTest {

    @Test
    void shouldAcceptValidEngineeringThresholds() {
        assertThatCode(() -> thresholds(0.92, 0.85, 0.60, 0.85, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOutOfRangeOrInconsistentThresholds() {
        assertThatThrownBy(() -> thresholds(-0.01, 0.0, 0.60, 0.85, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> thresholds(1.01, 0.85, 0.60, 0.85, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> thresholds(0.85, 0.86, 0.60, 0.85, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> thresholds(0.92, 0.85, 0.60, 0.85, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static VerificationThresholdProperties thresholds(
            double strong,
            double possible,
            double authorOverlap,
            double sourceMatch,
            int yearTolerance
    ) {
        return new VerificationThresholdProperties(strong, possible, authorOverlap, sourceMatch, yearTolerance);
    }
}
