package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
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

/** Performs guarded Crossref DOI lookups without logging request-sensitive values. */
@Component
public class CrossrefClient {

    private final RestClient restClient;
    private final CrossrefProperties properties;
    private final CrossrefRequestGate requestGate;
    private final CrossrefRetryPolicy retryPolicy;

    public CrossrefClient(
            @Qualifier("crossrefRestClient") RestClient restClient,
            CrossrefProperties properties,
            CrossrefRequestGate requestGate,
            CrossrefRetryPolicy retryPolicy
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.requestGate = requestGate;
        this.retryPolicy = retryPolicy;
    }

    public CrossrefWorkResponse getWorkByDoi(String doi) {
        ensureConfigured(doi);
        int completedRequests = 0;
        while (true) {
            try {
                completedRequests++;
                return requestGate.execute(() -> requestWork(doi));
            } catch (CrossrefApiException exception) {
                if (!retryPolicy.shouldRetry(exception, completedRequests)) {
                    throw exception;
                }
                retryPolicy.backoff(exception, completedRequests);
            }
        }
    }

    private CrossrefWorkResponse requestWork(String doi) {
        try {
            CrossrefWorkResponse response = restClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, doi))
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                    .headers(headers -> addPlusToken(headers))
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (request, clientResponse) -> fail(CrossrefFailureType.UNAUTHORIZED))
                    .onStatus(status -> status.value() == 403, (request, clientResponse) -> fail(CrossrefFailureType.FORBIDDEN))
                    .onStatus(status -> status.value() == 404, (request, clientResponse) -> fail(CrossrefFailureType.NOT_FOUND))
                    .onStatus(status -> status.value() == 429, (request, clientResponse) -> {
                        throw new CrossrefApiException(
                                CrossrefFailureType.RATE_LIMITED,
                                "Crossref 请求受到限流",
                                parseRetryAfter(clientResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                        );
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> fail(CrossrefFailureType.CLIENT_ERROR))
                    .onStatus(HttpStatusCode::is5xxServerError, (request, clientResponse) -> {
                        throw new CrossrefApiException(
                                CrossrefFailureType.SERVER_ERROR,
                                "Crossref 返回服务端错误",
                                parseRetryAfter(clientResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                        );
                    })
                    .body(CrossrefWorkResponse.class);
            validateResponse(response);
            return response;
        } catch (CrossrefApiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCause(exception, HttpTimeoutException.class)) {
                throw new CrossrefApiException(CrossrefFailureType.TIMEOUT, "Crossref 请求超时");
            }
            throw new CrossrefApiException(CrossrefFailureType.TRANSPORT_ERROR, "无法连接 Crossref");
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "Crossref 响应无法解析");
        }
    }

    private java.net.URI buildUri(UriBuilder uriBuilder, String doi) {
        return uriBuilder.pathSegment("works", doi)
                .queryParam("mailto", properties.getMailto().trim())
                .build();
    }

    private void addPlusToken(HttpHeaders headers) {
        if (StringUtils.hasText(properties.getPlusToken())) {
            headers.set("Crossref-Plus-API-Token", "Bearer " + properties.getPlusToken().trim());
        }
    }

    private void ensureConfigured(String doi) {
        if (!properties.isEnabled()) {
            throw new CrossrefApiException(CrossrefFailureType.DISABLED, "Crossref 检索未启用");
        }
        if (!StringUtils.hasText(properties.getMailto())) {
            throw new CrossrefApiException(CrossrefFailureType.MAILTO_MISSING, "Crossref mailto 未配置");
        }
        if (!StringUtils.hasText(properties.getUserAgent())) {
            throw new CrossrefApiException(CrossrefFailureType.USER_AGENT_MISSING, "Crossref User-Agent 未配置");
        }
        if (!StringUtils.hasText(doi)) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_REQUEST, "Crossref DOI 不能为空");
        }
    }

    private void validateResponse(CrossrefWorkResponse response) {
        if (response == null) {
            throw new CrossrefApiException(CrossrefFailureType.EMPTY_RESPONSE, "Crossref 返回空响应体");
        }
        if (!"ok".equalsIgnoreCase(response.status())
                || response.message() == null
                || !StringUtils.hasText(response.message().doi())) {
            throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "Crossref 响应结构无效");
        }
    }

    private void fail(CrossrefFailureType type) {
        throw new CrossrefApiException(type, "Crossref 请求未成功完成");
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
