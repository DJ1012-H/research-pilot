package com.dj1012h.researchpilot.integration.openalex;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAlexProperties.class)
public class OpenAlexConfig {

    @Bean
    RestClient openAlexRestClient(RestClient.Builder builder, OpenAlexProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(positive(properties.getConnectTimeout(), "connect-timeout"));
        requestFactory.setReadTimeout(positive(properties.getReadTimeout(), "read-timeout"));

        return builder
                .baseUrl(requireBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private Duration positive(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException("app.openalex." + property + " 必须大于 0");
        }
        return duration;
    }

    private String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.openalex.base-url 不能为空");
        }
        return baseUrl.trim();
    }
}
