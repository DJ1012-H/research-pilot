package com.dj1012h.researchpilot.integration.crossref;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in smoke test. It never runs during ordinary automated tests. */
@EnabledIfEnvironmentVariable(named = "CROSSREF_SMOKE_ENABLED", matches = "true")
class CrossrefRealApiSmokeTest {

    @Test
    void shouldFetchKnownCrossrefWork() {
        CrossrefProperties properties = new CrossrefProperties();
        properties.setEnabled(true);
        properties.setMailto(System.getenv("CROSSREF_MAILTO"));
        properties.setUserAgent(System.getenv().getOrDefault("CROSSREF_USER_AGENT", "ResearchPilot/0.1"));
        properties.setPlusToken(System.getenv("CROSSREF_PLUS_TOKEN"));
        CrossrefConfig.validate(properties);
        CrossrefRequestGate gate = new CrossrefRequestGate(properties);
        CrossrefClient client = new CrossrefClient(RestClient.builder().baseUrl(properties.getBaseUrl()).build(),
                properties, gate, new CrossrefRetryPolicy(properties));
        CrossrefSearchAdapter adapter = new CrossrefSearchAdapter(client);

        CrossrefLookupResult result = adapter.findByDoi("10.1038/s41586-021-03819-2");

        assertThat(result.status()).isEqualTo(CrossrefLookupResult.Status.FOUND);
        assertThat(result.metadata().doi()).isNotBlank();
    }
}
