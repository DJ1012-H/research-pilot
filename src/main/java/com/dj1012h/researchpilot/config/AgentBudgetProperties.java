package com.dj1012h.researchpilot.config;

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
    private int maxBusinessSteps = 8;
    private int maxUniqueCandidates = 45;
    private int maxCrossrefCalls = 45;
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
}
