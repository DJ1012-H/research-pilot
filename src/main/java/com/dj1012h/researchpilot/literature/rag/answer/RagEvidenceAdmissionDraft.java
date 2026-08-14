package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Strict DTO mapped only after syntax and schema validation. */
public record RagEvidenceAdmissionDraft(
        boolean relevant,
        List<String> admittedEvidenceIds,
        String reason
) {
    public RagEvidenceAdmissionDraft {
        admittedEvidenceIds = List.copyOf(Objects.requireNonNull(
                admittedEvidenceIds, "admittedEvidenceIds must not be null"));
        reason = Objects.requireNonNull(reason, "reason must not be null");
    }
}
