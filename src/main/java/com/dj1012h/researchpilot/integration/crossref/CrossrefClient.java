package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorksResponse;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Performs guarded Crossref DOI and bibliographic lookups without logging request-sensitive values. */
@Component
public class CrossrefClient {

    private static final String BIBLIOGRAPHIC_SELECT =
            "DOI,title,author,published,published-online,published-print,issued,created,container-title,type,publisher,abstract";

    private final RestClient restClient;
    private final CrossrefProperties properties;
    private final CrossrefRequestGate requestGate;
    private final CrossrefRetryPolicy retryPolicy;
    private final DoiNormalizer doiNormalizer;

    public CrossrefClient(
            @Qualifier("crossrefRestClient") RestClient restClient,
            CrossrefProperties properties,
            CrossrefRequestGate requestGate,
            CrossrefRetryPolicy retryPolicy,
            DoiNormalizer doiNormalizer
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.requestGate = requestGate;
        this.retryPolicy = retryPolicy;
        this.doiNormalizer = doiNormalizer;
    }

    public CrossrefWorkResponse getWorkByDoi(String doi) {
        ensureConfigured();
        String normalizedDoi = doiNormalizer.normalize(doi);
        if (normalizedDoi == null) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_REQUEST, "Invalid Crossref DOI");
        }
        return executeWithRetry(() -> requestWork(normalizedDoi));
    }

    public CrossrefWorksResponse getWorksByBibliographic(CrossrefBibliographicQuery query) {
        ensureConfigured();
        return executeWithRetry(() -> requestWorks(query));
    }

    private <T> T executeWithRetry(CrossrefRequestGate.CheckedSupplier<T> request) {
        int completedRequests = 0;
        while (true) {
            try {
                completedRequests++;
                return requestGate.execute(request);
            } catch (CrossrefApiException exception) {
                if (!retryPolicy.shouldRetry(exception, completedRequests)) throw exception;
                retryPolicy.backoff(exception, completedRequests);
            }
        }
    }

    private CrossrefWorkResponse requestWork(String doi) {
        try {
            CrossrefWorkResponse response = restClient.get()
                    .uri(uriBuilder -> buildDoiUri(uriBuilder, doi))
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                    .headers(this::addPlusToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, clientResponse) -> fail(CrossrefFailureType.INVALID_RESPONSE))
                    .onStatus(status -> status.value() == 401, (request, clientResponse) -> fail(CrossrefFailureType.UNAUTHORIZED))
                    .onStatus(status -> status.value() == 403, (request, clientResponse) -> fail(CrossrefFailureType.FORBIDDEN))
                    .onStatus(status -> status.value() == 404, (request, clientResponse) -> fail(CrossrefFailureType.NOT_FOUND))
                    .onStatus(status -> status.value() == 429, (request, clientResponse) -> rateLimited(clientResponse.getHeaders()))
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> fail(CrossrefFailureType.CLIENT_ERROR))
                    .onStatus(HttpStatusCode::is5xxServerError, (request, clientResponse) -> serverError(clientResponse.getHeaders()))
                    .body(CrossrefWorkResponse.class);
            validateWorkResponse(response);
            return response;
        } catch (CrossrefApiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw transportFailure(exception);
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "Crossref response could not be parsed");
        }
    }

    private CrossrefWorksResponse requestWorks(CrossrefBibliographicQuery query) {
        try {
            CrossrefWorksResponse response = restClient.get()
                    .uri(uriBuilder -> buildBibliographicUri(uriBuilder, query))
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                    .headers(this::addPlusToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, clientResponse) -> fail(CrossrefFailureType.INVALID_RESPONSE))
                    .onStatus(status -> status.value() == 401, (request, clientResponse) -> fail(CrossrefFailureType.UNAUTHORIZED))
                    .onStatus(status -> status.value() == 403, (request, clientResponse) -> fail(CrossrefFailureType.FORBIDDEN))
                    .onStatus(status -> status.value() == 404, (request, clientResponse) -> fail(CrossrefFailureType.NOT_FOUND))
                    .onStatus(status -> status.value() == 429, (request, clientResponse) -> rateLimited(clientResponse.getHeaders()))
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> fail(CrossrefFailureType.CLIENT_ERROR))
                    .onStatus(HttpStatusCode::is5xxServerError, (request, clientResponse) -> serverError(clientResponse.getHeaders()))
                    .body(CrossrefWorksResponse.class);
            validateWorksResponse(response);
            return response;
        } catch (CrossrefApiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw transportFailure(exception);
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "Crossref response could not be parsed");
        }
    }

    private java.net.URI buildDoiUri(UriBuilder uriBuilder, String doi) {
        return uriBuilder.pathSegment("works", doi)
                .queryParam("mailto", properties.getMailto().trim())
                .build();
    }

    private java.net.URI buildBibliographicUri(UriBuilder uriBuilder, CrossrefBibliographicQuery query) {
        return uriBuilder.path("/works")
                .queryParam("query.bibliographic", query.queryText())
                .queryParam("rows", properties.getBibliographicRows())
                .queryParam("select", BIBLIOGRAPHIC_SELECT)
                .queryParam("mailto", properties.getMailto().trim())
                .build();
    }

    private void addPlusToken(HttpHeaders headers) {
        if (StringUtils.hasText(properties.getPlusToken())) {
            headers.set("Crossref-Plus-API-Token", "Bearer " + properties.getPlusToken().trim());
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) throw new CrossrefApiException(CrossrefFailureType.DISABLED, "Crossref is disabled");
        if (!StringUtils.hasText(properties.getMailto())) {
            throw new CrossrefApiException(CrossrefFailureType.MAILTO_MISSING, "Crossref mailto is missing");
        }
        if (!StringUtils.hasText(properties.getUserAgent())) {
            throw new CrossrefApiException(CrossrefFailureType.USER_AGENT_MISSING, "Crossref User-Agent is missing");
        }
        if (properties.getBibliographicRows() < 1 || properties.getBibliographicRows() > 10) {
            throw new IllegalStateException("app.crossref.bibliographic-rows must be between 1 and 10");
        }
    }

    private void validateWorkResponse(CrossrefWorkResponse response) {
        if (response == null) throw new CrossrefApiException(CrossrefFailureType.EMPTY_RESPONSE, "Crossref returned an empty body");
        if (!"ok".equalsIgnoreCase(response.status()) || response.message() == null
                || !StringUtils.hasText(response.message().doi())) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "Crossref response structure is invalid");
        }
    }

    private void validateWorksResponse(CrossrefWorksResponse response) {
        if (response == null) throw new CrossrefApiException(CrossrefFailureType.EMPTY_RESPONSE, "Crossref returned an empty body");
        if (!"ok".equalsIgnoreCase(response.status()) || response.message() == null || response.message().items() == null) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "Crossref response structure is invalid");
        }
    }

    private CrossrefApiException transportFailure(ResourceAccessException exception) {
        if (hasCause(exception, SocketTimeoutException.class) || hasCause(exception, HttpTimeoutException.class)) {
            return new CrossrefApiException(CrossrefFailureType.TIMEOUT, "Crossref request timed out");
        }
        return new CrossrefApiException(CrossrefFailureType.TRANSPORT_ERROR, "Crossref could not be reached");
    }

    private void rateLimited(HttpHeaders headers) {
        throw new CrossrefApiException(CrossrefFailureType.RATE_LIMITED, "Crossref rate limited the request",
                parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)));
    }

    private void serverError(HttpHeaders headers) {
        throw new CrossrefApiException(CrossrefFailureType.SERVER_ERROR, "Crossref returned a server error",
                parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)));
    }

    private void fail(CrossrefFailureType type) {
        throw new CrossrefApiException(type, "Crossref request did not complete");
    }

    private Duration parseRetryAfter(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (RuntimeException invalidDate) {
                return null;
            }
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) return true;
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }
}
