package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic, side-effect-free similarity calculations for bibliographic text. */
@Component
public class BibliographicSimilarityCalculator {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final TitleNormalizer titleNormalizer;
    private final VenueNormalizer venueNormalizer;

    public BibliographicSimilarityCalculator(TitleNormalizer titleNormalizer, VenueNormalizer venueNormalizer) {
        this.titleNormalizer = Objects.requireNonNull(titleNormalizer, "titleNormalizer must not be null");
        this.venueNormalizer = Objects.requireNonNull(venueNormalizer, "venueNormalizer must not be null");
    }

    public double titleSimilarity(String candidateTitle, String referenceTitle) {
        return combinedSimilarity(
                requireNormalized(titleNormalizer.normalize(candidateTitle), "candidateTitle"),
                requireNormalized(titleNormalizer.normalize(referenceTitle), "referenceTitle")
        );
    }

    public double venueSimilarity(String candidateVenue, String referenceVenue) {
        String candidate = requireNormalized(venueNormalizer.normalize(candidateVenue), "candidateVenue");
        String reference = requireNormalized(venueNormalizer.normalize(referenceVenue), "referenceVenue");
        if (candidate.equals(reference)) {
            return 1.0;
        }
        return tokenJaccard(candidate, reference);
    }

    static double combinedSimilarity(String normalizedCandidate, String normalizedReference) {
        if (normalizedCandidate.equals(normalizedReference)) {
            return 1.0;
        }
        return boundedScore(tokenJaccard(normalizedCandidate, normalizedReference) * 0.65
                + normalizedEditSimilarity(normalizedCandidate, normalizedReference) * 0.35);
    }

    static double tokenJaccard(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        if (union.isEmpty()) {
            throw new IllegalArgumentException("normalized values must contain a token");
        }
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        return (double) intersection.size() / union.size();
    }

    static double normalizedEditSimilarity(String left, String right) {
        int maximumLength = Math.max(left.length(), right.length());
        if (maximumLength == 0) {
            throw new IllegalArgumentException("normalized values must not be empty");
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitutionCost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + substitutionCost
                );
            }
            int[] temporary = previous;
            previous = current;
            current = temporary;
        }
        return boundedScore(1.0 - (double) previous[right.length()] / maximumLength);
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = new HashSet<>();
        var matcher = TOKEN.matcher(value);
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    private static String requireNormalized(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must contain a usable value");
        }
        return value;
    }

    private static double boundedScore(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
