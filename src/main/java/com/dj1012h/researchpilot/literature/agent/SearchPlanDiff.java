package com.dj1012h.researchpilot.literature.agent;

import java.util.List;
import java.util.Objects;

/** Explainable, immutable difference between the current and refined plans. */
public record SearchPlanDiff(
        List<String> addedKeywords,
        List<String> removedKeywords,
        List<String> preservedUserConstraints,
        String reason
) {
    public SearchPlanDiff {
        addedKeywords = List.copyOf(Objects.requireNonNull(
                addedKeywords,
                "addedKeywords must not be null"
        ));
        removedKeywords = List.copyOf(Objects.requireNonNull(
                removedKeywords,
                "removedKeywords must not be null"
        ));
        preservedUserConstraints = List.copyOf(Objects.requireNonNull(
                preservedUserConstraints,
                "preservedUserConstraints must not be null"
        ));
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
