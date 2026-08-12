package com.dj1012h.researchpilot.literature.rag.answer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Server-controlled, hard-bounded limits for the disabled-by-default ask route. */
@Component
@ConfigurationProperties(prefix = "app.rag.answer")
public class RagAnswerProperties {

    public static final int HARD_MAX_QUESTION_LENGTH = 1_000;
    public static final int HARD_MAX_TOP_K = 10;
    public static final int HARD_MAX_EVIDENCE = 5;
    public static final int HARD_MAX_SEGMENT_CHARS = 4_000;
    public static final int HARD_MAX_CONTEXT_CHARS = 20_000;
    public static final int HARD_MAX_PROMPT_CHARS = 32_000;
    public static final int HARD_MAX_RAW_DRAFT_CHARS = 12_000;
    public static final int HARD_MAX_REPAIR_PROMPT_CHARS = 48_000;
    public static final Duration HARD_MAX_TOTAL_TIMEOUT = Duration.ofSeconds(90);

    private boolean enabled;
    private int defaultTopK = 5;
    private int maxTopK = HARD_MAX_TOP_K;
    private int maxEvidence = HARD_MAX_EVIDENCE;
    private int maxSegmentChars = HARD_MAX_SEGMENT_CHARS;
    private int maxContextChars = HARD_MAX_CONTEXT_CHARS;
    private int maxPromptChars = HARD_MAX_PROMPT_CHARS;
    private int maxRawDraftChars = HARD_MAX_RAW_DRAFT_CHARS;
    private int maxRepairPromptChars = HARD_MAX_REPAIR_PROMPT_CHARS;
    private Duration totalTimeout = HARD_MAX_TOTAL_TIMEOUT;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int value) { defaultTopK = bounded(value, 1, maxTopK, "default-top-k"); }
    public int getMaxTopK() { return maxTopK; }
    public void setMaxTopK(int value) { maxTopK = bounded(value, 1, HARD_MAX_TOP_K, "max-top-k"); }
    public int getMaxEvidence() { return maxEvidence; }
    public void setMaxEvidence(int value) { maxEvidence = bounded(value, 1, HARD_MAX_EVIDENCE, "max-evidence"); }
    public int getMaxSegmentChars() { return maxSegmentChars; }
    public void setMaxSegmentChars(int value) { maxSegmentChars = bounded(value, 1, HARD_MAX_SEGMENT_CHARS, "max-segment-chars"); }
    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int value) { maxContextChars = bounded(value, 1, HARD_MAX_CONTEXT_CHARS, "max-context-chars"); }
    public int getMaxPromptChars() { return maxPromptChars; }
    public void setMaxPromptChars(int value) { maxPromptChars = bounded(value, 1, HARD_MAX_PROMPT_CHARS, "max-prompt-chars"); }
    public int getMaxRawDraftChars() { return maxRawDraftChars; }
    public void setMaxRawDraftChars(int value) { maxRawDraftChars = bounded(value, 1, HARD_MAX_RAW_DRAFT_CHARS, "max-raw-draft-chars"); }
    public int getMaxRepairPromptChars() { return maxRepairPromptChars; }
    public void setMaxRepairPromptChars(int value) { maxRepairPromptChars = bounded(value, 1, HARD_MAX_REPAIR_PROMPT_CHARS, "max-repair-prompt-chars"); }
    public Duration getTotalTimeout() { return totalTimeout; }
    public void setTotalTimeout(Duration value) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(HARD_MAX_TOTAL_TIMEOUT) > 0) {
            throw new IllegalArgumentException("total-timeout must be positive and no more than 90 seconds");
        }
        totalTimeout = value;
    }

    private int bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
