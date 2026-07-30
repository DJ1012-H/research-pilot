package com.dj1012h.researchpilot.integration.cache;

import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.integration.openalex.CachedOpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexProperties;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchAdapter;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_CACHE_SMOKE", matches = "(?i)true")
class RedisCacheSmokeTest {

    @Test
    void realRedisShowsMissThenHitWithBoundedTtlAndExactCleanup() {
        LiteratureCacheProperties properties = new LiteratureCacheProperties();
        properties.setEnabled(true);
        properties.setKeyPrefix("research-pilot:literature:v1:smoke-" + UUID.randomUUID());
        OpenAlexProperties openAlexProperties = new OpenAlexProperties();
        CacheKeyFactory keys = new CacheKeyFactory(
                properties, new DoiNormalizer(), openAlexProperties, new CrossrefProperties());
        OpenAlexQuery query = query();
        String exactKey = keys.openAlexSearch(query);

        LettuceConnectionFactory factory = configuredFactory();
        StringRedisTemplate redis = template(factory);
        try {
            assertThat(redis.hasKey(exactKey)).isFalse();
            OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
            OpenAlexSearchResult expected = new OpenAlexSearchResult(0, List.of(), null);
            when(delegate.search(query)).thenReturn(expected);
            CachedOpenAlexSearchPort port = new CachedOpenAlexSearchPort(
                    delegate,
                    keys,
                    new LiteratureCacheService(
                            properties, new RedisCacheStore(redis), new ObjectMapper(), Clock.systemUTC()),
                    properties
            );

            assertThat(port.search(query)).isEqualTo(expected);
            assertThat(port.search(query)).isEqualTo(expected);

            verify(delegate, times(1)).search(query);
            Long ttlSeconds = redis.getExpire(exactKey, TimeUnit.SECONDS);
            assertThat(ttlSeconds)
                    .isPositive()
                    .isLessThanOrEqualTo(properties.getOpenalexTtl().toSeconds());
        } finally {
            redis.delete(exactKey);
            assertThat(redis.hasKey(exactKey)).isFalse();
            factory.destroy();
        }
    }

    @Test
    void unreachableRedisFailsOpenToTheExistingAdapter() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(250))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
        StringRedisTemplate redis = template(factory);
        try {
            LiteratureCacheProperties properties = new LiteratureCacheProperties();
            properties.setEnabled(true);
            OpenAlexProperties openAlexProperties = new OpenAlexProperties();
            CacheKeyFactory keys = new CacheKeyFactory(
                    properties, new DoiNormalizer(), openAlexProperties, new CrossrefProperties());
            OpenAlexQuery query = query();
            OpenAlexSearchAdapter delegate = mock(OpenAlexSearchAdapter.class);
            OpenAlexSearchResult expected = new OpenAlexSearchResult(0, List.of(), null);
            when(delegate.search(query)).thenReturn(expected);
            CachedOpenAlexSearchPort port = new CachedOpenAlexSearchPort(
                    delegate,
                    keys,
                    new LiteratureCacheService(
                            properties, new RedisCacheStore(redis), new ObjectMapper(), Clock.systemUTC()),
                    properties
            );

            assertThat(port.search(query)).isEqualTo(expected);
            verify(delegate).search(query);
        } finally {
            factory.destroy();
        }
    }

    private LettuceConnectionFactory configuredFactory() {
        String host = requiredEnvironment("REDIS_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        int database = Integer.parseInt(System.getenv().getOrDefault("REDIS_DATABASE", "0"));
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(database);
        String username = System.getenv("REDIS_USERNAME");
        if (username != null && !username.isBlank()) {
            configuration.setUsername(username);
        }
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        return new LettuceConnectionFactory(configuration);
    }

    private StringRedisTemplate template(LettuceConnectionFactory factory) {
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the opt-in Redis smoke test");
        }
        return value;
    }

    private OpenAlexQuery query() {
        return new OpenAlexQuery(
                "redis smoke",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1),
                List.of("article"),
                List.of("en"),
                OpenAlexQuery.Sort.RELEVANCE,
                3
        );
    }
}
