package com.dj1012h.researchpilot.literature.model;

/** Describes where a resolved search constraint value actually came from. */
public enum ConstraintOrigin {
    USER_EXPLICIT,
    SYSTEM_DEFAULT,
    MODEL_DERIVED,
    SYSTEM_FIXED
}
