package com.dj1012h.researchpilot.common.ai;

/** Bounded model output metadata used for auditable usage observations. */
public record ModelInvocationResult(
        String content,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Integer totalTokenCount
) {
    public ModelInvocationResult {
        if (content == null) throw new IllegalArgumentException("content must not be null");
        validateNonNegative(inputTokenCount, "inputTokenCount");
        validateNonNegative(outputTokenCount, "outputTokenCount");
        validateNonNegative(totalTokenCount, "totalTokenCount");
    }

    private static void validateNonNegative(Integer value, String name) {
        if (value != null && value < 0) throw new IllegalArgumentException(name + " must not be negative");
    }
}
