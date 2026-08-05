package com.dj1012h.researchpilot.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** Records only bounded operational dimensions; identifiers and user content are excluded. */
@Component
public class LiteratureObservationMetrics {

    private static final Set<String> MODEL_OPERATIONS = Set.of("chat", "search_plan", "review", "action_decision");
    private static final Set<String> PERSISTENCE_OPERATIONS =
            Set.of("CREATE_RUNNING_TASK", "FINALIZE_SUCCESS", "FINALIZE_FAILURE");
    private final MeterRegistry registry;

    public LiteratureObservationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    private LiteratureObservationMetrics() {
        this.registry = null;
    }

    public static LiteratureObservationMetrics noop() {
        return new LiteratureObservationMetrics();
    }

    public void recordRequest(String outcome, Duration duration) {
        recordTimer("researchpilot.literature.request.duration", duration, "outcome", outcome(outcome));
    }

    public void recordAgentOutcome(String terminationReason) {
        if (registry == null) return;
        Counter.builder("researchpilot.literature.agent.runs")
                .tag("termination_reason", boundedReason(terminationReason))
                .register(registry)
                .increment();
    }

    public void recordModel(String operation, String outcome, String failureType, Duration duration) {
        recordTimer("researchpilot.literature.external.duration", duration,
                "provider", "llm", "operation", MODEL_OPERATIONS.contains(operation) ? operation : "other",
                "outcome", outcome(outcome), "failure_type", failureType == null ? "none" : boundedReason(failureType));
    }

    public void recordProvider(String provider, String operation, String outcome, Duration duration) {
        String safeProvider = "openalex".equals(provider) || "crossref".equals(provider) ? provider : "other";
        String safeOperation = "search".equals(operation) || "doi_lookup".equals(operation)
                || "bibliographic_lookup".equals(operation) ? operation : "other";
        recordTimer("researchpilot.literature.external.duration", duration,
                "provider", safeProvider, "operation", safeOperation, "outcome", outcome(outcome));
    }

    public void recordPersistence(String operation, String outcome, Duration duration) {
        recordTimer("researchpilot.literature.persistence.duration", duration,
                "operation", PERSISTENCE_OPERATIONS.contains(operation) ? operation : "other",
                "outcome", outcome(outcome));
    }

    public void recordCache(String provider, String operation, String result, Duration duration) {
        String safeProvider = "openalex".equals(provider) || "crossref".equals(provider) ? provider : "other";
        String safeOperation = "search".equals(operation) || "doi".equals(operation)
                || "bibliographic".equals(operation) ? operation : "other";
        recordTimer("researchpilot.literature.cache.duration", duration,
                "provider", safeProvider, "operation", safeOperation,
                "cache_result", boundedReason(result));
    }

    private void recordTimer(String name, Duration duration, String... tags) {
        if (registry == null) return;
        Timer.builder(name).tags(tags).register(registry).record(duration.isNegative() ? Duration.ZERO : duration);
    }

    private String outcome(String value) {
        return "succeeded".equals(value) || "failed".equals(value) || "rejected".equals(value) ? value : "other";
    }

    private String boundedReason(String value) {
        if (value == null || value.isBlank()) return "none";
        return value.length() <= 64 && value.matches("[A-Za-z0-9_]+") ? value : "other";
    }
}
