package com.dj1012h.researchpilot.literature.review;

/** Stable reference to the one-based position of a formal paper. */
public record CitationId(int formalPaperPosition) {

    public CitationId {
        if (formalPaperPosition < 1) {
            throw new IllegalArgumentException("formalPaperPosition must be positive");
        }
    }

    public String value() {
        return "P" + formalPaperPosition;
    }
}
