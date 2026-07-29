package com.dj1012h.researchpilot.config;

import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/** Fixed, server-controlled limits for one literature research task. */
@Component
@ConfigurationProperties(prefix = "app.research-agent")
public class AgentBudgetProperties {

    private int maxSearchRounds = 2;
    private int maxPlanAdjustments = 1;
    // Initial plan + two four-step retrieval rounds + one controlled refinement.
    private int maxBusinessSteps = 10;
    private int maxUniqueCandidates = 45;
    private int maxCrossrefCalls = 45;
    private int maxRefinementKeywords = 5;
    private int maxRefinementKeywordLength = SearchPlan.MAX_KEYWORD_LENGTH;
    private int maxRefinementReasonLength = 300;
    private Duration totalTimeout = Duration.ofSeconds(90);

    public int getMaxSearchRounds() { return maxSearchRounds; }
    public void setMaxSearchRounds(int value) { maxSearchRounds = positive(value, "max-search-rounds"); }
    public int getMaxPlanAdjustments() { return maxPlanAdjustments; }
    public void setMaxPlanAdjustments(int value) { maxPlanAdjustments = positive(value, "max-plan-adjustments"); }
    public int getMaxBusinessSteps() { return maxBusinessSteps; }
    public void setMaxBusinessSteps(int value) { maxBusinessSteps = positive(value, "max-business-steps"); }
    public int getMaxUniqueCandidates() { return maxUniqueCandidates; }
    public void setMaxUniqueCandidates(int value) { maxUniqueCandidates = positive(value, "max-unique-candidates"); }
    public int getMaxCrossrefCalls() { return maxCrossrefCalls; }
    public void setMaxCrossrefCalls(int value) { maxCrossrefCalls = positive(value, "max-crossref-calls"); }
    public int getMaxRefinementKeywords() { return maxRefinementKeywords; }
    public void setMaxRefinementKeywords(int value) {
        maxRefinementKeywords = bounded(
                value,
                SearchPlan.MAX_KEYWORD_COUNT,
                "max-refinement-keywords"
        );
    }
    public int getMaxRefinementKeywordLength() { return maxRefinementKeywordLength; }
    public void setMaxRefinementKeywordLength(int value) {
        maxRefinementKeywordLength = bounded(
                value,
                SearchPlan.MAX_KEYWORD_LENGTH,
                "max-refinement-keyword-length"
        );
    }
    public int getMaxRefinementReasonLength() { return maxRefinementReasonLength; }
    public void setMaxRefinementReasonLength(int value) {
        maxRefinementReasonLength = positive(value, "max-refinement-reason-length");
    }
    public Duration getTotalTimeout() { return totalTimeout; }
    public void setTotalTimeout(Duration value) {
        totalTimeout = Objects.requireNonNull(value, "total-timeout must not be null");
        if (totalTimeout.isZero() || totalTimeout.isNegative()) {
            throw new IllegalArgumentException("total-timeout must be positive");
        }
    }

    private int positive(int value, String property) {
        if (value < 1) throw new IllegalArgumentException(property + " must be positive");
        return value;
    }

    private int bounded(int value, int maximum, String property) {
        int positiveValue = positive(value, property);
        if (positiveValue > maximum) {
            throw new IllegalArgumentException(property + " must not exceed " + maximum);
        }
        return positiveValue;
    }
}
