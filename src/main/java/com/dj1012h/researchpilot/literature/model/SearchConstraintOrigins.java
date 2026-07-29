package com.dj1012h.researchpilot.literature.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, complete field-to-origin map for one trusted plan context. */
public final class SearchConstraintOrigins {

    private final Map<SearchConstraintField, ConstraintOrigin> origins;

    public SearchConstraintOrigins(Map<SearchConstraintField, ConstraintOrigin> origins) {
        Objects.requireNonNull(origins, "origins must not be null");
        EnumMap<SearchConstraintField, ConstraintOrigin> copied =
                new EnumMap<>(SearchConstraintField.class);
        copied.putAll(origins);
        for (SearchConstraintField field : SearchConstraintField.values()) {
            if (copied.get(field) == null) {
                throw new IllegalArgumentException("missing constraint origin for " + field);
            }
        }
        this.origins = Collections.unmodifiableMap(copied);
    }

    public ConstraintOrigin originOf(SearchConstraintField field) {
        Objects.requireNonNull(field, "field must not be null");
        ConstraintOrigin origin = origins.get(field);
        if (origin == null) {
            throw new IllegalStateException("constraint origin is unavailable for " + field);
        }
        return origin;
    }

    public boolean isUserExplicit(SearchConstraintField field) {
        return originOf(field) == ConstraintOrigin.USER_EXPLICIT;
    }

    public Map<SearchConstraintField, ConstraintOrigin> asMap() {
        return origins;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof SearchConstraintOrigins that
                && origins.equals(that.origins);
    }

    @Override
    public int hashCode() {
        return origins.hashCode();
    }

    @Override
    public String toString() {
        return "SearchConstraintOrigins" + origins;
    }
}
