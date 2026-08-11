package com.dj1012h.researchpilot.integration.qdrant;

import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QdrantProperties.class)
public class QdrantConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.rag.qdrant", name = "enabled", havingValue = "true")
    RestClient qdrantRestClient(RestClient.Builder builder, QdrantProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(positive(properties.getConnectTimeout(), "connect-timeout"));
        requestFactory.setReadTimeout(positive(properties.getReadTimeout(), "read-timeout"));
        return builder
                .baseUrl(requireText(properties.getBaseUrl(), "base-url"))
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.rag.qdrant", name = "enabled", havingValue = "true")
    RagIndexPort qdrantIndexPort(
            @Qualifier("qdrantRestClient") RestClient restClient,
            QdrantProperties properties
    ) {
        return new QdrantIndexAdapter(restClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.rag.qdrant", name = "enabled", havingValue = "true")
    RagIndexDefinition ragIndexDefinition(QdrantProperties properties, RagEmbeddingProfile embeddingProfile) {
        return new RagIndexDefinition(
                properties.getCollectionName(),
                embeddingProfile.version(),
                embeddingProfile.expectedDimensions());
    }

    private Duration positive(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException("app.rag.qdrant." + property + " must be positive");
        }
        return duration;
    }

    private String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.rag.qdrant." + property + " must not be blank");
        }
        return value.strip();
    }
}
