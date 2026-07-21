package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CrossrefProperties.class)
public class CrossrefConfig {

    @Bean
    RestClient crossrefRestClient(RestClient.Builder builder, CrossrefProperties properties) {
        validate(properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl(properties.getBaseUrl().trim()).requestFactory(requestFactory).build();
    }

    static void validate(CrossrefProperties properties) {
        requireText(properties.getBaseUrl(), "base-url");
        positive(properties.getConnectTimeout(), "connect-timeout");
        positive(properties.getReadTimeout(), "read-timeout");
        positive(properties.getInitialBackoff(), "initial-backoff");
        positive(properties.getMaxBackoff(), "max-backoff");
        if (properties.getInitialBackoff().compareTo(properties.getMaxBackoff()) > 0) {
            throw new IllegalStateException("app.crossref.initial-backoff 不能大于 max-backoff");
        }
        if (properties.getMaxConcurrency() < 1) {
            throw new IllegalStateException("app.crossref.max-concurrency 必须大于 0");
        }
        if (properties.getRequestsPerSecond() < 1) {
            throw new IllegalStateException("app.crossref.requests-per-second 必须大于 0");
        }
        if (properties.getMaxRetries() < 0) {
            throw new IllegalStateException("app.crossref.max-retries 不能小于 0");
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("app.crossref." + name + " 必须大于 0");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.crossref." + name + " 不能为空");
        }
    }
}
