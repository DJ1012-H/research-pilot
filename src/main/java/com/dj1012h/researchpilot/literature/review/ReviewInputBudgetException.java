package com.dj1012h.researchpilot.literature.review;

/** Safe, content-free signal that a review input or prompt exceeded its hard budget. */
public class ReviewInputBudgetException extends RuntimeException {

    private final String code;

    public ReviewInputBudgetException(String code) {
        super("Review input budget exceeded: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
