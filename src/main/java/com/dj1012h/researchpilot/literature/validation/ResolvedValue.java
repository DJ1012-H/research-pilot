package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;

import java.util.Objects;

/** Internal value-and-provenance pair used only while resolving a plan draft. */
record ResolvedValue<T>(T value, ConstraintOrigin origin) {
    ResolvedValue {
        value = Objects.requireNonNull(value, "value must not be null");
        origin = Objects.requireNonNull(origin, "origin must not be null");
    }
}
