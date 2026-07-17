package com.dj1012h.researchpilot.literature.model;

import java.util.List;
import java.util.Objects;

/**
 * Structured and explainable evidence produced by the verification pipeline.
 *
 * <p>{@code evidenceScore} is an engineering score in the range [0, 1], not a
 * statistical probability. A missing score means that the system could not
 * make a reliable judgement.</p>
 */
public record VerificationResult(
        VerificationStatus status,
        Double evidenceScore,
        VerificationSource source,
        String referenceDoi,
        List<FieldVerification> fieldResults,
        List<String> reasons
) {

    public VerificationResult {
        status = Objects.requireNonNull(status, "status 不能为空");
        source = Objects.requireNonNull(source, "source 不能为空");
        fieldResults = List.copyOf(Objects.requireNonNull(fieldResults, "fieldResults 不能为空"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons 不能为空"));
        validateScore(evidenceScore, "evidenceScore");
    }

    public static VerificationResult notChecked() {
        return new VerificationResult(
                VerificationStatus.NOT_CHECKED,
                null,
                VerificationSource.CROSSREF,
                null,
                List.of(),
                List.of("尚未执行 Crossref 核验")
        );
    }

    public record FieldVerification(
            String field,
            FieldStatus status,
            String candidateValue,
            String referenceValue,
            Double similarity,
            String reason
    ) {
        public FieldVerification {
            field = requireText(field, "field");
            status = Objects.requireNonNull(status, "status 不能为空");
            validateScore(similarity, "similarity");
        }
    }

    public enum VerificationStatus {
        NOT_CHECKED,
        VERIFIED,
        PARTIALLY_VERIFIED,
        CONFLICTED,
        NOT_FOUND,
        SOURCE_UNAVAILABLE,
        REJECTED
    }

    public enum FieldStatus {
        MATCHED,
        EXPLAINABLE_DIFFERENCE,
        MISMATCHED,
        UNKNOWN
    }

    public enum VerificationSource {
        CROSSREF
    }

    private static void validateScore(Double score, String field) {
        if (score != null && (score < 0.0 || score > 1.0 || score.isNaN())) {
            throw new IllegalArgumentException(field + " 必须在 0 到 1 之间");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
