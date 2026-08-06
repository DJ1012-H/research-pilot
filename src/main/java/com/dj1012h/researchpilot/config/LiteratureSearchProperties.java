package com.dj1012h.researchpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.literature.search")
public class LiteratureSearchProperties {

    public static final int DEFAULT_RESULT_LIMIT = 5;
    public static final int MAX_RESULT_LIMIT = 15;
    public static final int MAX_CROSSREF_LOOKUPS_PER_REQUEST = 10;

    private int defaultResultLimit = DEFAULT_RESULT_LIMIT;
    private int maxResultLimit = MAX_RESULT_LIMIT;
    private int candidateMultiplier = 3;
    private int maxCandidateLimit = 100;
    private int maxCrossrefLookupsPerRequest = MAX_CROSSREF_LOOKUPS_PER_REQUEST;
    private int earliestSupportedYear = 1900;

    public int getDefaultResultLimit() {
        return defaultResultLimit;
    }

    public void setDefaultResultLimit(int defaultResultLimit) {
        if (defaultResultLimit < 1 || defaultResultLimit > MAX_RESULT_LIMIT) {
            throw new IllegalArgumentException(
                    "default-result-limit must be between 1 and " + MAX_RESULT_LIMIT);
        }
        this.defaultResultLimit = defaultResultLimit;
    }

    public int getMaxResultLimit() {
        return maxResultLimit;
    }

    public void setMaxResultLimit(int maxResultLimit) {
        if (maxResultLimit != MAX_RESULT_LIMIT) {
            throw new IllegalArgumentException(
                    "max-result-limit is fixed at " + MAX_RESULT_LIMIT);
        }
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

    public int getMaxCrossrefLookupsPerRequest() {
        return maxCrossrefLookupsPerRequest;
    }

    public void setMaxCrossrefLookupsPerRequest(int maxCrossrefLookupsPerRequest) {
        if (maxCrossrefLookupsPerRequest < 1
                || maxCrossrefLookupsPerRequest > MAX_CROSSREF_LOOKUPS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "max-crossref-lookups-per-request 必须在 1 到 "
                            + MAX_CROSSREF_LOOKUPS_PER_REQUEST + " 之间");
        }
        this.maxCrossrefLookupsPerRequest = maxCrossrefLookupsPerRequest;
    }

    public int getEarliestSupportedYear() {
        return earliestSupportedYear;
    }

    public void setEarliestSupportedYear(int earliestSupportedYear) {
        this.earliestSupportedYear = earliestSupportedYear;
    }
}
