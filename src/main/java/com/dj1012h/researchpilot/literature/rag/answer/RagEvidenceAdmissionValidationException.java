package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.Objects;

/** Safe low-cardinality admission validation failure; never contains model content. */
public final class RagEvidenceAdmissionValidationException extends IllegalArgumentException {
    private final String validationCode;

    public RagEvidenceAdmissionValidationException(String validationCode) {
        super(requireCode(validationCode));
        this.validationCode = validationCode;
    }

    public String validationCode() {
        return validationCode;
    }

    private static String requireCode(String value) {
        Objects.requireNonNull(value, "validationCode must not be null");
        if (!value.matches("RAG_ADMISSION_[A-Z0-9_]+")) {
            throw new IllegalArgumentException("invalid admission validation code");
        }
        return value;
    }
}
