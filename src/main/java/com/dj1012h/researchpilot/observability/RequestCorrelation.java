package com.dj1012h.researchpilot.observability;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Logging-only request correlation. Business state is passed explicitly through
 * method arguments; MDC is never used as an Agent input.
 */
public final class RequestCorrelation {

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String TASK_ID_KEY = "taskId";

    private RequestCorrelation() { }

    public static UUID requestIdOrNew() {
        return currentRequestId().orElseGet(UUID::randomUUID);
    }

    public static Optional<UUID> currentRequestId() {
        String value = MDC.get(REQUEST_ID_KEY);
        if (value == null || value.length() != 36) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static String requestIdForLog() {
        return currentRequestId().map(UUID::toString).orElse("none");
    }

    public static void bindTaskId(UUID taskId) {
        MDC.put(TASK_ID_KEY, taskId.toString());
    }

    public static void clearTaskId() {
        MDC.remove(TASK_ID_KEY);
    }
}
