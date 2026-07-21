package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CrossrefClientTest {

    private static final String BASE_URL = "https://crossref.test";
    private static final String MAILTO = "test@example.com";

    @Test
    void shouldBuildEncodedDoiRequestAndMapMinimumResponse() {
        Fixture fixture = fixture(enabledProperties());
        fixture.server.expect(requestTo(BASE_URL + "/works/10.1000%2Fexample?mailto=test@example.com"))
                .andExpect(header(HttpHeaders.USER_AGENT, "ResearchPilot-test"))
                .andExpect(header("Crossref-Plus-API-Token", "Bearer fake-token"))
                .andRespond(withSuccess(okJson(), MediaType.APPLICATION_JSON));

        CrossrefWorkResponse response = fixture.client.getWorkByDoi("10.1000/example");

        assertThat(response.message().doi()).isEqualTo("10.1000/example");
        fixture.server.verify();
    }

    @Test
    void shouldClassifyNonRetryableResponsesAndInvalidBodies() {
        assertStatus(HttpStatus.UNAUTHORIZED, CrossrefFailureType.UNAUTHORIZED);
        assertStatus(HttpStatus.FORBIDDEN, CrossrefFailureType.FORBIDDEN);
        assertStatus(HttpStatus.NOT_FOUND, CrossrefFailureType.NOT_FOUND);
        assertStatus(HttpStatus.BAD_REQUEST, CrossrefFailureType.CLIENT_ERROR);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, CrossrefFailureType.SERVER_ERROR);

        Fixture invalid = fixture(enabledProperties());
        invalid.server.expect(requestTo(BASE_URL + "/works/10.1000%2Fexample?mailto=test@example.com"))
                .andRespond(withSuccess("{not-json}", MediaType.APPLICATION_JSON));
        assertFailure(invalid.client, CrossrefFailureType.INVALID_RESPONSE);
        invalid.server.verify();
    }

    @Test
    void shouldPreferRetryAfterAndPreserveLastFailureClassification() {
        CrossrefProperties properties = enabledProperties();
        List<Duration> waits = new ArrayList<>();
        Fixture fixture = fixture(properties, waits);
        fixture.server.expect(requestTo(BASE_URL + "/works/10.1000%2Fexample?mailto=test@example.com"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1"));
        fixture.server.expect(requestTo(BASE_URL + "/works/10.1000%2Fexample?mailto=test@example.com"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server.expect(requestTo(BASE_URL + "/works/10.1000%2Fexample?mailto=test@example.com"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertFailure(fixture.client, CrossrefFailureType.SERVER_ERROR);
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofMillis(500));
        fixture.server.verify();
    }

    @Test
    void shouldRejectDisabledAndMissingConfigurationBeforeHttp() {
        CrossrefProperties disabled = enabledProperties();
        disabled.setEnabled(false);
        assertFailure(fixture(disabled).client, CrossrefFailureType.DISABLED);
        CrossrefProperties missingMailto = enabledProperties();
        missingMailto.setMailto(" ");
        assertFailure(fixture(missingMailto).client, CrossrefFailureType.MAILTO_MISSING);
        CrossrefProperties missingAgent = enabledProperties();
        missingAgent.setUserAgent(" ");
        assertFailure(fixture(missingAgent).client, CrossrefFailureType.USER_AGENT_MISSING);
    }

    @Test
    void shouldClassifyTransportTimeoutWithoutLeakingSensitiveValues() throws Exception {
        ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);
        when(requestFactory.createRequest(any(URI.class), eq(org.springframework.http.HttpMethod.GET)))
                .thenThrow(new SocketTimeoutException("timed out"));
        CrossrefProperties properties = enabledProperties();
        CrossrefClient client = client(RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build(), properties, new ArrayList<>());

        assertThatThrownBy(() -> client.getWorkByDoi("10.1000/example"))
                .isInstanceOfSatisfying(CrossrefApiException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(CrossrefFailureType.TIMEOUT);
                    assertThat(exception.getMessage()).doesNotContain(MAILTO).doesNotContain("fake-token");
                });
    }

    private void assertStatus(HttpStatus status, CrossrefFailureType expected) {
        CrossrefProperties properties = enabledProperties();
        properties.setMaxRetries(0);
        Fixture fixture = fixture(properties);
        fixture.server.expect(requestTo(BASE_URL + "/works/10.1000%2Fexample?mailto=test@example.com"))
                .andRespond(withStatus(status));
        assertFailure(fixture.client, expected);
        fixture.server.verify();
    }

    private void assertFailure(CrossrefClient client, CrossrefFailureType expected) {
        assertThatThrownBy(() -> client.getWorkByDoi("10.1000/example"))
                .isInstanceOfSatisfying(CrossrefApiException.class,
                        exception -> assertThat(exception.getFailureType()).isEqualTo(expected));
    }

    private Fixture fixture(CrossrefProperties properties) { return fixture(properties, new ArrayList<>()); }

    private Fixture fixture(CrossrefProperties properties, List<Duration> waits) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(client(builder.build(), properties, waits), server);
    }

    private CrossrefClient client(RestClient restClient, CrossrefProperties properties, List<Duration> waits) {
        CrossrefRequestGate gate = new CrossrefRequestGate(properties,
                Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC), duration -> { });
        return new CrossrefClient(restClient, properties, gate,
                new CrossrefRetryPolicy(properties, waits::add));
    }

    private CrossrefProperties enabledProperties() {
        CrossrefProperties properties = new CrossrefProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE_URL);
        properties.setMailto(MAILTO);
        properties.setUserAgent("ResearchPilot-test");
        properties.setPlusToken("fake-token");
        return properties;
    }

    private String okJson() {
        return "{\"status\":\"ok\",\"message\":{\"DOI\":\"10.1000/example\",\"title\":[\"Example\"]}}";
    }

    private record Fixture(CrossrefClient client, MockRestServiceServer server) { }
}
