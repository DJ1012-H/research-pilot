package com.dj1012h.researchpilot.literature.agent;

import java.util.List;
import java.util.Objects;

/** Strictly mapped, untrusted model proposal containing refinable fields only. */
public record SearchPlanRefinementDraft(
        List<String> synonyms,
        List<String> abbreviations,
        List<String> conceptCombinations,
        String reason
) {
    public SearchPlanRefinementDraft {
        synonyms = copy(synonyms, "synonyms");
        abbreviations = copy(abbreviations, "abbreviations");
        conceptCombinations = copy(conceptCombinations, "conceptCombinations");
    }

    private static List<String> copy(List<String> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " must not be null"));
    }
}
