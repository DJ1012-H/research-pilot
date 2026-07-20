package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import com.dj1012h.researchpilot.literature.validation.ValidationStage;

import java.util.List;

public class SearchPlanGenerationException extends RuntimeException {

    private final ValidationStage finalStage;
    private final List<ValidationIssue> issues;

    public SearchPlanGenerationException(SearchPlanValidationException cause) {
        super("Search plan generation failed after validation", cause);
        this.finalStage = cause.getStage();
        this.issues = List.copyOf(cause.getIssues());
    }

    public ValidationStage getFinalStage() {
        return finalStage;
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
