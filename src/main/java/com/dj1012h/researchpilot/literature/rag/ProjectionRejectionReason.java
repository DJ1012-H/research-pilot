package com.dj1012h.researchpilot.literature.rag;

/** Explicit reasons why an authoritative paper was not admitted for embedding. */
public enum ProjectionRejectionReason {
    INVALID_INPUT,
    INVALID_PAPER_ID,
    STATUS_NOT_VERIFIED,
    DOI_MISSING,
    DOI_INVALID,
    DOI_NOT_NORMALIZED,
    DOI_MISMATCH,
    VERIFICATION_VERSION_MISMATCH,
    SOURCE_UPDATED_AT_MISSING,
    ILLEGAL_SEPARATOR
}
