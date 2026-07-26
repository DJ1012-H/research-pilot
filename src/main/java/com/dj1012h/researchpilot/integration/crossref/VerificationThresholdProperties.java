package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Version-one engineering thresholds for bibliographic field evidence.
 * They are not probabilities or statistically calibrated confidence values.
 */
@ConfigurationProperties(prefix = "app.crossref.verification.thresholds")
public record VerificationThresholdProperties(
        double titleStrongMatch,
        double titlePossibleMatch,
        double authorOverlap,
        double sourceMatch,
        int publicationYearTolerance
) {

    public VerificationThresholdProperties {
        validateUnitInterval(titleStrongMatch, "titleStrongMatch");
        validateUnitInterval(titlePossibleMatch, "titlePossibleMatch");
        validateUnitInterval(authorOverlap, "authorOverlap");
        validateUnitInterval(sourceMatch, "sourceMatch");
        if (titlePossibleMatch > titleStrongMatch) {
            throw new IllegalArgumentException("titlePossibleMatch must not exceed titleStrongMatch");
        }
        if (publicationYearTolerance < 0) {
            throw new IllegalArgumentException("publicationYearTolerance must not be negative");
        }
    }

    private static void validateUnitInterval(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }
}
