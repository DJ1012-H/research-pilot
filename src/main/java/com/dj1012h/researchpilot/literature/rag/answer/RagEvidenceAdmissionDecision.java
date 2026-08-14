package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;

/** Java-validated relevance decision; the reason is internal and never published. */
public record RagEvidenceAdmissionDecision(
        boolean relevant,
        List<String> admittedEvidenceIds,
        String reason
) {
    public RagEvidenceAdmissionDecision {
        admittedEvidenceIds = List.copyOf(Objects.requireNonNull(
                admittedEvidenceIds, "admittedEvidenceIds must not be null"));
        reason = Objects.requireNonNull(reason, "reason must not be null");
        if (relevant != !admittedEvidenceIds.isEmpty()) {
            throw new IllegalArgumentException("relevant must match admitted evidence presence");
        }
    }
}
