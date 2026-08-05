package com.dj1012h.researchpilot.integration.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dj1012h.researchpilot.observability.LiteratureObservationMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Cache-aside serialization, envelope validation, exact-key eviction, and fail-open cooldown. */
@Component
public class LiteratureCacheService {

    private static final String SCHEMA_VERSION = "v1";
    private static final Logger log = LoggerFactory.getLogger(LiteratureCacheService.class);

    private final LiteratureCacheProperties properties;
    private final CacheStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LiteratureObservationMetrics metrics;
    private volatile Instant cacheUnavailableUntil = Instant.MIN;

    public LiteratureCacheService(
            LiteratureCacheProperties properties,
            CacheStore store,
            ObjectMapper objectMapper,
            @Qualifier("systemClock") Clock clock
    ) {
        this(properties, store, objectMapper, clock, LiteratureObservationMetrics.noop());
    }

    @Autowired
    public LiteratureCacheService(
            LiteratureCacheProperties properties,
            CacheStore store,
            ObjectMapper objectMapper,
            @Qualifier("systemClock") Clock clock,
            LiteratureObservationMetrics metrics
    ) {
        this.properties = properties;
        this.store = store;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    public <T> Optional<T> read(String key, CacheValueKind kind, Class<T> type) {
        long startedAt = System.nanoTime();
        if (!properties.isEnabled()) {
            record(kind, "READ", "BYPASS_DISABLED", startedAt);
            return Optional.empty();
        }
        if (clock.instant().isBefore(cacheUnavailableUntil)) {
            record(kind, "READ", "BYPASS_COOLDOWN", startedAt);
            return Optional.empty();
        }
        Optional<String> encoded;
        try {
            encoded = store.get(key);
        } catch (RuntimeException exception) {
            markRedisUnavailable();
            record(kind, "READ", "READ_FAILURE", startedAt);
            return Optional.empty();
        }
        if (encoded.isEmpty()) {
            record(kind, "READ", "MISS", startedAt);
            return Optional.empty();
        }
        try {
            if (encoded.get().getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                evictCorrupt(key);
                record(kind, "READ", "CORRUPT", startedAt);
                return Optional.empty();
            }
            CacheEnvelope envelope = objectMapper.readValue(encoded.get(), CacheEnvelope.class);
            if (!SCHEMA_VERSION.equals(envelope.schemaVersion())
                    || !kind.provider().equals(envelope.provider())
                    || !kind.operation().equals(envelope.operation())
                    || kind != envelope.resultKind()
                    || envelope.payload() == null) {
                evictCorrupt(key);
                record(kind, "READ", "CORRUPT", startedAt);
                return Optional.empty();
            }
            T value = objectMapper.treeToValue(envelope.payload(), type);
            markRedisAvailable();
            record(kind, "READ", "HIT", startedAt);
            return Optional.of(value);
        } catch (RuntimeException | java.io.IOException exception) {
            evictCorrupt(key);
            record(kind, "READ", "CORRUPT", startedAt);
            return Optional.empty();
        }
    }

    public void write(String key, CacheValueKind kind, Object value, Duration ttl) {
        long startedAt = System.nanoTime();
        if (!properties.isEnabled()) {
            record(kind, "WRITE", "BYPASS_DISABLED", startedAt);
            return;
        }
        if (clock.instant().isBefore(cacheUnavailableUntil)) {
            record(kind, "WRITE", "BYPASS_COOLDOWN", startedAt);
            return;
        }
        try {
            JsonNode payload = objectMapper.valueToTree(value);
            String encoded = objectMapper.writeValueAsString(new CacheEnvelope(
                    SCHEMA_VERSION, kind.provider(), kind.operation(), kind, payload));
            if (encoded.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                record(kind, "WRITE", "SKIPPED_OVERSIZE", startedAt);
                return;
            }
            store.put(key, encoded, ttl);
            markRedisAvailable();
            record(kind, "WRITE", "SUCCEEDED", startedAt);
        } catch (RuntimeException | java.io.IOException exception) {
            markRedisUnavailable();
            record(kind, "WRITE", "WRITE_FAILURE", startedAt);
        }
    }

    private void record(CacheValueKind kind, String phase, String outcome, long startedAt) {
        long durationMs = Math.max(
                0,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        );
        log.info(
                "event=literature_cache_access provider={} operation={} phase={} outcome={} durationMs={}",
                kind.provider(), kind.operation(), phase, outcome, durationMs
        );
        metrics.recordCache(kind.provider(), kind.operation(), outcome, Duration.ofMillis(durationMs));
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
