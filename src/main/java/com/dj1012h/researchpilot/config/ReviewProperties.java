package com.dj1012h.researchpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-controlled hard limits for abstract-level review generation. */
@Component
@ConfigurationProperties(prefix = "app.review")
public class ReviewProperties {

    public static final int HARD_MAX_EVIDENCE_PAPERS = 20;
    public static final int HARD_MAX_ABSTRACT_CHARS = 4_000;
    public static final int HARD_MAX_EVIDENCE_JSON_LENGTH = 64_000;
    public static final int HARD_MAX_INITIAL_PROMPT_LENGTH = 80_000;
    public static final int HARD_MAX_RAW_DRAFT_LENGTH = 16_384;
    public static final int HARD_MAX_REPAIR_PROMPT_LENGTH = 96_000;

    private int maxEvidencePapers = HARD_MAX_EVIDENCE_PAPERS;
    private int maxAbstractChars = HARD_MAX_ABSTRACT_CHARS;
    private int maxEvidenceJsonLength = HARD_MAX_EVIDENCE_JSON_LENGTH;
    private int maxInitialPromptLength = HARD_MAX_INITIAL_PROMPT_LENGTH;
    private int maxRawDraftLength = HARD_MAX_RAW_DRAFT_LENGTH;
    private int maxRepairPromptLength = HARD_MAX_REPAIR_PROMPT_LENGTH;

    public int getMaxEvidencePapers() {
        return maxEvidencePapers;
    }

    public void setMaxEvidencePapers(int value) {
        maxEvidencePapers = bounded(value, HARD_MAX_EVIDENCE_PAPERS, "max-evidence-papers");
    }

    public int getMaxAbstractChars() {
        return maxAbstractChars;
    }

    public void setMaxAbstractChars(int value) {
        maxAbstractChars = bounded(value, HARD_MAX_ABSTRACT_CHARS, "max-abstract-chars");
    }

    public int getMaxEvidenceJsonLength() {
        return maxEvidenceJsonLength;
    }

    public void setMaxEvidenceJsonLength(int value) {
        maxEvidenceJsonLength = bounded(
                value, HARD_MAX_EVIDENCE_JSON_LENGTH, "max-evidence-json-length");
    }

    public int getMaxInitialPromptLength() {
        return maxInitialPromptLength;
    }

    public void setMaxInitialPromptLength(int value) {
        maxInitialPromptLength = bounded(
                value, HARD_MAX_INITIAL_PROMPT_LENGTH, "max-initial-prompt-length");
    }

    public int getMaxRawDraftLength() {
        return maxRawDraftLength;
    }

    public void setMaxRawDraftLength(int value) {
        maxRawDraftLength = bounded(value, HARD_MAX_RAW_DRAFT_LENGTH, "max-raw-draft-length");
    }

    public int getMaxRepairPromptLength() {
        return maxRepairPromptLength;
    }

    public void setMaxRepairPromptLength(int value) {
        maxRepairPromptLength = bounded(
                value, HARD_MAX_REPAIR_PROMPT_LENGTH, "max-repair-prompt-length");
    }

    private int bounded(int value, int maximum, String property) {
        if (value < 1) {
            throw new IllegalArgumentException(property + " must be positive");
        }
        if (value > maximum) {
            throw new IllegalArgumentException(property + " must not exceed " + maximum);
        }
        return value;
    }
}
