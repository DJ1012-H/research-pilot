package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Performs one OpenAlex HTTP request and deserializes the external response.
 */
@Component
public class OpenAlexClient {
        //与 OpenAlex API 进行实际网络通信
    static final String SELECT_FIELDS = String.join(",",
            "id",
            "doi",
            "title",
            "publication_year",
            "publication_date",
            "type",
            "cited_by_count",
            "authorships",
            "primary_location",
            "best_oa_location",
            "abstract_inverted_index"
    );

    private final RestClient restClient;
    private final OpenAlexProperties properties;

    public OpenAlexClient(@Qualifier("openAlexRestClient") RestClient restClient,
                          OpenAlexProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public OpenAlexWorksResponse search(OpenAlexQuery query) {
        ensureConfigured();

        try {
            OpenAlexWorksResponse response = restClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, query))
                    .retrieve()
                    .onStatus(status -> status.value() == 429, (request, clientResponse) -> {
                        throw new OpenAlexApiException(
                                OpenAlexFailureType.RATE_LIMITED,
                                "OpenAlex 请求受到限流"
                        );
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                        throw new OpenAlexApiException(
                                OpenAlexFailureType.CLIENT_ERROR,
                                "OpenAlex 返回客户端错误 HTTP " + clientResponse.getStatusCode().value()
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, clientResponse) -> {
                        throw new OpenAlexApiException(
                                OpenAlexFailureType.SERVER_ERROR,
                                "OpenAlex 返回服务端错误 HTTP " + clientResponse.getStatusCode().value()
                        );
                    })
                    .body(OpenAlexWorksResponse.class);

            if (response == null) {
                throw new OpenAlexApiException(
                        OpenAlexFailureType.EMPTY_RESPONSE,
                        "OpenAlex 返回空响应体"
                );
            }
            return response;
        } catch (OpenAlexApiException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCause(exception, HttpTimeoutException.class)) {
                throw new OpenAlexApiException(
                        OpenAlexFailureType.TIMEOUT,
                        "OpenAlex 请求超时"
                );
            }
            throw new OpenAlexApiException(
                    OpenAlexFailureType.TRANSPORT_ERROR,
                    "无法连接 OpenAlex"
            );
        } catch (HttpMessageConversionException | RestClientException exception) {
            throw new OpenAlexApiException(
                    OpenAlexFailureType.INVALID_RESPONSE,
                    "OpenAlex 响应无法解析"
            );
        }
    }

    private java.net.URI buildUri(UriBuilder uriBuilder, OpenAlexQuery query) {
        int pageSize = query.pageSizeOrDefault(properties.getDefaultPageSize());
        return uriBuilder
                .path("/works")
                .queryParam("search", query.search())
                .queryParam("filter", buildFilter(query))
                .queryParam("sort", query.sort().apiValue())
                .queryParam("per_page", pageSize)
                .queryParam("select", SELECT_FIELDS)
                .queryParam("api_key", properties.getApiKey())
                .build();
    }

    private String buildFilter(OpenAlexQuery query) {
        List<String> filters = new ArrayList<>();
        filters.add("from_publication_date:" + query.fromPublicationDate());
        filters.add("to_publication_date:" + query.toPublicationDate());
        if (!query.workTypes().isEmpty()) {
            filters.add("type:" + String.join("|", query.workTypes()));
        }
        return String.join(",", filters);
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new OpenAlexApiException(
                    OpenAlexFailureType.DISABLED,
                    "OpenAlex 检索未启用"
            );
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new OpenAlexApiException(
                    OpenAlexFailureType.API_KEY_MISSING,
                    "OpenAlex API Key 未配置"
            );
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }
}
