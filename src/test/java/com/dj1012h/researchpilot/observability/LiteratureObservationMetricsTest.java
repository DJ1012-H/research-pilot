package com.dj1012h.researchpilot.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LiteratureObservationMetricsTest {

    @Test
    void shouldUseOnlyBoundedOperationalTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LiteratureObservationMetrics metrics = new LiteratureObservationMetrics(registry);

        metrics.recordRequest("succeeded", Duration.ofMillis(5));
        metrics.recordAgentOutcome("TARGET_REACHED");
        metrics.recordModel("search_plan", "failed", "TIMEOUT", Duration.ofMillis(2));
        metrics.recordProvider("crossref", "doi_lookup", "succeeded", Duration.ofMillis(3));
        metrics.recordPersistence("FINALIZE_FAILURE", "failed", Duration.ofMillis(1));
        metrics.recordCache("openalex", "search", "HIT", Duration.ofMillis(1));

        assertThat(registry.find("researchpilot.literature.request.duration").timer()).isNotNull();
        assertThat(registry.find("researchpilot.literature.agent.runs")
                .tag("termination_reason", "TARGET_REACHED").counter()).isNotNull();
        assertThat(registry.find("researchpilot.literature.external.duration")
                .tags("provider", "crossref", "operation", "doi_lookup", "outcome", "succeeded").timer()).isNotNull();
        assertThat(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey())).doesNotContain("requestId", "taskId", "traceId", "query", "doi", "title", "url");
    }
}
