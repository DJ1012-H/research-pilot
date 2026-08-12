package com.dj1012h.researchpilot.literature.rag.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-controlled limits for the Day 4 diagnostics-only retrieval API. */
@Component
@ConfigurationProperties(prefix = "app.rag.retrieval")
public class RagRetrievalProperties {

    private boolean enabled;
    private int defaultTopK = 5;
    private int maxTopK = 20;
    private int candidateMultiplier = 3;
    private int maxCandidatePoints = 60;
    private int maxQueryLength = 1_000;
    private int maxPaperIds = 50;
    private int maxExcerptChars = 500;
    private int earliestSupportedYear = 1900;
    private int latestSupportedYear = 2100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int value) { defaultTopK = bounded(value, 1, maxTopK, "default-top-k"); }
    public int getMaxTopK() { return maxTopK; }
    public void setMaxTopK(int value) { maxTopK = positive(value, "max-top-k"); }
    public int getCandidateMultiplier() { return candidateMultiplier; }
    public void setCandidateMultiplier(int value) { candidateMultiplier = positive(value, "candidate-multiplier"); }
    public int getMaxCandidatePoints() { return maxCandidatePoints; }
    public void setMaxCandidatePoints(int value) { maxCandidatePoints = positive(value, "max-candidate-points"); }
    public int getMaxQueryLength() { return maxQueryLength; }
    public void setMaxQueryLength(int value) { maxQueryLength = positive(value, "max-query-length"); }
    public int getMaxPaperIds() { return maxPaperIds; }
    public void setMaxPaperIds(int value) { maxPaperIds = positive(value, "max-paper-ids"); }
    public int getMaxExcerptChars() { return maxExcerptChars; }
    public void setMaxExcerptChars(int value) { maxExcerptChars = positive(value, "max-excerpt-chars"); }
    public int getEarliestSupportedYear() { return earliestSupportedYear; }
    public void setEarliestSupportedYear(int value) { earliestSupportedYear = value; }
    public int getLatestSupportedYear() { return latestSupportedYear; }
    public void setLatestSupportedYear(int value) { latestSupportedYear = value; }

    private int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private int bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
