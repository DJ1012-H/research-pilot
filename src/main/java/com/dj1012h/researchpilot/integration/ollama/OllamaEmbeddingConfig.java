package com.dj1012h.researchpilot.integration.ollama;

import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OllamaEmbeddingProperties.class)
public class OllamaEmbeddingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.rag.embedding.ollama", name = "enabled", havingValue = "true")
    RestClient ollamaEmbeddingRestClient(RestClient.Builder builder, OllamaEmbeddingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(positive(properties.getConnectTimeout(), "connect-timeout"));
        requestFactory.setReadTimeout(positive(properties.getReadTimeout(), "read-timeout"));
        return builder
                .baseUrl(requireBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.rag.embedding.ollama", name = "enabled", havingValue = "true")
    EmbeddingPort ollamaEmbeddingPort(
            @Qualifier("ollamaEmbeddingRestClient") RestClient ollamaEmbeddingRestClient,
            OllamaEmbeddingProperties properties
    ) {
        return new OllamaEmbeddingAdapter(ollamaEmbeddingRestClient, properties);
    }

    private Duration positive(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException("app.rag.embedding.ollama." + property + " must be positive");
        }
        return duration;
    }

    private String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.rag.embedding.ollama.base-url must not be blank");
        }
        return baseUrl.strip();
    }
}
