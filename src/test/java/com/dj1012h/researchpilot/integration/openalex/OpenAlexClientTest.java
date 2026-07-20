package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAlexClientTest {

    private static final String BASE_URL = "https://openalex.test";
    private static final String FAKE_API_KEY = "test-openalex-key";

    @Test
    void shouldBuildWorksRequestAndDeserializeResponse() throws IOException {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> {
                    assertThat(queryParam(request.getURI(), "search"))
                            .isEqualTo("Mamba remote sensing");
                    assertThat(queryParam(request.getURI(), "filter"))
                            .isEqualTo(
                                    "from_publication_date:2022-01-01,"
                                            + "to_publication_date:2026-12-31,"
                                            + "type:article|review,"
                                            + "language:en|zh"
                            );
                    assertThat(queryParam(request.getURI(), "sort"))
                            .isEqualTo("relevance_score:desc");
                    assertThat(queryParam(request.getURI(), "per_page")).isEqualTo("25");
                    assertThat(queryParam(request.getURI(), "select"))
                            .isEqualTo(OpenAlexClient.SELECT_FIELDS);
                    assertThat(queryParam(request.getURI(), "api_key")).isEqualTo(FAKE_API_KEY);
                })
                .andRespond(withSuccess(fixtureJson(), MediaType.APPLICATION_JSON));

        OpenAlexWorksResponse response = fixture.client().search(query(25));

        assertThat(response.meta().count()).isEqualTo(123);
        assertThat(response.meta().nextCursor()).isEqualTo("next-cursor-value");
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().id())
                .isEqualTo("https://openalex.org/W3177828909");
        fixture.server().verify();
    }

    @Test
    void shouldOmitLanguageFilterWhenNoLanguagesRequested() {
        ClientFixture fixture = fixture(enabledProperties());
        OpenAlexQuery query = new OpenAlexQuery(
                "Mamba remote sensing",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of("article"),
                OpenAlexQuery.Sort.RELEVANCE,
                10
        );
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andExpect(request -> assertThat(queryParam(request.getURI(), "filter"))
                        .doesNotContain("language:"))
                .andRespond(withSuccess("{\"meta\":{\"count\":0},\"results\":[]}", MediaType.APPLICATION_JSON));

        fixture.client().search(query);

        fixture.server().verify();
    }

    @Test
    void shouldUseConfiguredDefaultPageSizeWhenQueryOmitsPerPage() {
        OpenAlexProperties properties = enabledProperties();
        properties.setDefaultPageSize(30);
        ClientFixture fixture = fixture(properties);
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andExpect(request -> assertThat(queryParam(request.getURI(), "per_page"))
                        .isEqualTo("30"))
                .andRespond(withSuccess("{\"meta\":{\"count\":0},\"results\":[]}", MediaType.APPLICATION_JSON));

        fixture.client().search(query(null));

        fixture.server().verify();
    }

    @Test
    void shouldDeserializeEmptyResultsAsSuccessfulResponse() {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andRespond(withSuccess(
                        "{\"meta\":{\"count\":0,\"next_cursor\":null},\"results\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        OpenAlexWorksResponse response = fixture.client().search(query(10));

        assertThat(response.meta().count()).isZero();
        assertThat(response.results()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void shouldClassifyFourHundredResponse() {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertFailureType(fixture.client(), OpenAlexFailureType.CLIENT_ERROR);
        fixture.server().verify();
    }

    @Test
    void shouldClassifyRateLimitResponse() {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertFailureType(fixture.client(), OpenAlexFailureType.RATE_LIMITED);
        fixture.server().verify();
    }

    @Test
    void shouldClassifyFiveHundredResponse() {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertFailureType(fixture.client(), OpenAlexFailureType.SERVER_ERROR);
        fixture.server().verify();
    }

    @Test
    void shouldRejectEmptyResponseBody() {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andRespond(withSuccess());

        assertFailureType(fixture.client(), OpenAlexFailureType.EMPTY_RESPONSE);
        fixture.server().verify();
    }

    @Test
    void shouldClassifyInvalidJsonWithoutReturningEmptyResults() {
        ClientFixture fixture = fixture(enabledProperties());
        fixture.server().expect(requestTo(startsWith(BASE_URL + "/works")))
                .andRespond(withSuccess("{not-json}", MediaType.APPLICATION_JSON));

        assertFailureType(fixture.client(), OpenAlexFailureType.INVALID_RESPONSE);
        fixture.server().verify();
    }

    @Test
    void shouldRejectDisabledIntegrationBeforeSendingRequest() {
        OpenAlexProperties properties = enabledProperties();
        properties.setEnabled(false);

        assertFailureType(fixture(properties).client(), OpenAlexFailureType.DISABLED);
    }

    @Test
    void shouldRejectMissingApiKeyBeforeSendingRequest() {
        OpenAlexProperties properties = enabledProperties();
        properties.setApiKey(" ");

        assertFailureType(fixture(properties).client(), OpenAlexFailureType.API_KEY_MISSING);
    }

    @Test
    void shouldClassifySocketTimeout() throws IOException {
        ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);
        when(requestFactory.createRequest(any(URI.class), eq(HttpMethod.GET)))
                .thenThrow(new SocketTimeoutException("timed out"));
        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();

        assertFailureType(
                new OpenAlexClient(restClient, enabledProperties()),
                OpenAlexFailureType.TIMEOUT
        );
    }

    private void assertFailureType(OpenAlexClient client, OpenAlexFailureType expected) {
        assertThatThrownBy(() -> client.search(query(10)))
                .isInstanceOfSatisfying(OpenAlexApiException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(expected);
                    assertThat(exception.getMessage()).doesNotContain(FAKE_API_KEY);
                    assertThat(exception.getCause()).isNull();
                });
    }

    private ClientFixture fixture(OpenAlexProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new ClientFixture(new OpenAlexClient(builder.build(), properties), server);
    }

    private OpenAlexProperties enabledProperties() {
        OpenAlexProperties properties = new OpenAlexProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey(FAKE_API_KEY);
        return properties;
    }

    private OpenAlexQuery query(Integer perPage) {
        return new OpenAlexQuery(
                "Mamba remote sensing",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of("article", "review"),
                List.of("en", "zh"),
                OpenAlexQuery.Sort.RELEVANCE,
                perPage
        );
    }

    private String fixtureJson() throws IOException {
        return new ClassPathResource("openalex/works-response.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String queryParam(URI uri, String name) {
        String encoded = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
        return encoded == null ? null : URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private record ClientFixture(
            OpenAlexClient client,
            MockRestServiceServer server
    ) {
    }
}
