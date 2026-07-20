package com.dj1012h.researchpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.literature.search")
public class LiteratureSearchProperties {

    private int defaultResultLimit = 20;
    private int maxResultLimit = 50;
    private int candidateMultiplier = 3;
    private int maxCandidateLimit = 100;
    private int earliestSupportedYear = 1900;

    public int getDefaultResultLimit() {
        return defaultResultLimit;
    }

    public void setDefaultResultLimit(int defaultResultLimit) {
        this.defaultResultLimit = defaultResultLimit;
    }

    public int getMaxResultLimit() {
        return maxResultLimit;
    }

    public void setMaxResultLimit(int maxResultLimit) {
        this.maxResultLimit = maxResultLimit;
    }

    public int getCandidateMultiplier() {
        return candidateMultiplier;
    }

    public void setCandidateMultiplier(int candidateMultiplier) {
        this.candidateMultiplier = candidateMultiplier;
    }

    public int getMaxCandidateLimit() {
        return maxCandidateLimit;
    }

    public void setMaxCandidateLimit(int maxCandidateLimit) {
        this.maxCandidateLimit = maxCandidateLimit;
    }

    public int getEarliestSupportedYear() {
        return earliestSupportedYear;
    }

    public void setEarliestSupportedYear(int earliestSupportedYear) {
        this.earliestSupportedYear = earliestSupportedYear;
    }
}
