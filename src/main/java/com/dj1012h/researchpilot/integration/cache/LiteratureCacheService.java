package com.dj1012h.researchpilot.integration.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Cache-aside serialization, envelope validation, exact-key eviction, and fail-open cooldown. */
@Component
public class LiteratureCacheService {

    private static final String SCHEMA_VERSION = "v1";

    private final LiteratureCacheProperties properties;
    private final CacheStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile Instant cacheUnavailableUntil = Instant.MIN;

    public LiteratureCacheService(
            LiteratureCacheProperties properties,
            CacheStore store,
            ObjectMapper objectMapper,
            @Qualifier("systemClock") Clock clock
    ) {
        this.properties = properties;
        this.store = store;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public <T> Optional<T> read(String key, CacheValueKind kind, Class<T> type) {
        if (!canUseCache()) return Optional.empty();
        Optional<String> encoded;
        try {
            encoded = store.get(key);
        } catch (RuntimeException exception) {
            markRedisUnavailable();
            return Optional.empty();
        }
        if (encoded.isEmpty()) return Optional.empty();
        try {
            if (encoded.get().getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                evictCorrupt(key);
                return Optional.empty();
            }
            CacheEnvelope envelope = objectMapper.readValue(encoded.get(), CacheEnvelope.class);
            if (!SCHEMA_VERSION.equals(envelope.schemaVersion())
                    || !kind.provider().equals(envelope.provider())
                    || !kind.operation().equals(envelope.operation())
                    || kind != envelope.resultKind()
                    || envelope.payload() == null) {
                evictCorrupt(key);
                return Optional.empty();
            }
            T value = objectMapper.treeToValue(envelope.payload(), type);
            markRedisAvailable();
            return Optional.of(value);
        } catch (RuntimeException | java.io.IOException exception) {
            evictCorrupt(key);
            return Optional.empty();
        }
    }

    public void write(String key, CacheValueKind kind, Object value, Duration ttl) {
        if (!canUseCache()) return;
        try {
            JsonNode payload = objectMapper.valueToTree(value);
            String encoded = objectMapper.writeValueAsString(new CacheEnvelope(
                    SCHEMA_VERSION, kind.provider(), kind.operation(), kind, payload));
            if (encoded.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) return;
            store.put(key, encoded, ttl);
            markRedisAvailable();
        } catch (RuntimeException | java.io.IOException exception) {
            markRedisUnavailable();
        }
    }

    private boolean canUseCache() {
        return properties.isEnabled() && !clock.instant().isBefore(cacheUnavailableUntil);
    }

    private void evictCorrupt(String key) {
        try {
            store.delete(key);
        } catch (RuntimeException exception) {
            markRedisUnavailable();
        }
    }

    private void markRedisUnavailable() {
        cacheUnavailableUntil = clock.instant().plus(properties.getFailureCooldown());
    }

    private void markRedisAvailable() {
        cacheUnavailableUntil = Instant.MIN;
    }

    private record CacheEnvelope(
            String schemaVersion,
            String provider,
            String operation,
            CacheValueKind resultKind,
            JsonNode payload
    ) { }
}
