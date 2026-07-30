package com.dj1012h.researchpilot.integration.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LiteratureCacheServiceTest {

    @Test
    void corruptedPayloadIsEvictedByExactKeyAndNeverReturned() {
        RecordingStore store = new RecordingStore();
        store.values.put("key", "not-json");
        LiteratureCacheService cache = cache(store, new MutableClock());

        assertThat(cache.read("key", CacheValueKind.CROSSREF_DOI, String.class)).isEmpty();
        assertThat(store.deleted).containsExactly("key");
    }

    @Test
    void redisFailureOpensShortCooldownWithoutBlockingTheCaller() {
        RecordingStore store = new RecordingStore();
        store.failReads = true;
        MutableClock clock = new MutableClock();
        LiteratureCacheService cache = cache(store, clock);

        assertThat(cache.read("key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();
        assertThat(cache.read("key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();
        assertThat(store.readCount).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        assertThat(cache.read("key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();
        assertThat(store.readCount).isEqualTo(2);
    }

    @Test
    void disabledCacheDoesNotTouchRedis() {
        RecordingStore store = new RecordingStore();
        LiteratureCacheProperties properties = new LiteratureCacheProperties();
        properties.setEnabled(false);
        LiteratureCacheService cache = new LiteratureCacheService(properties, store, new ObjectMapper(), Clock.systemUTC());

        cache.write("key", CacheValueKind.OPENALEX_SEARCH, "value", Duration.ofMinutes(1));
        assertThat(cache.read("key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();
        assertThat(store.readCount).isZero();
        assertThat(store.values).isEmpty();
    }

    @Test
    void mismatchedEnvelopeMetadataIsEvictedInsteadOfReturned() {
        RecordingStore store = new RecordingStore();
        store.values.put("key", """
                {"schemaVersion":"v1","provider":"crossref","operation":"doi",
                 "resultKind":"CROSSREF_DOI","payload":"cached"}
                """);
        LiteratureCacheService cache = cache(store, new MutableClock());

        assertThat(cache.read("key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();
        assertThat(store.deleted).containsExactly("key");
    }

    @Test
    void oversizedCachedPayloadIsEvictedAndOversizedWritesAreSkipped() {
        RecordingStore store = new RecordingStore();
        LiteratureCacheProperties properties = new LiteratureCacheProperties();
        properties.setEnabled(true);
        properties.setMaxPayloadBytes(8);
        LiteratureCacheService cache = new LiteratureCacheService(
                properties, store, new ObjectMapper(), Clock.systemUTC());
        store.values.put("read-key", "too-large");

        assertThat(cache.read("read-key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();
        cache.write("write-key", CacheValueKind.OPENALEX_SEARCH, "too-large", Duration.ofMinutes(1));

        assertThat(store.deleted).containsExactly("read-key");
        assertThat(store.values).doesNotContainKey("write-key");
    }

    @Test
    void writeFailureOpensCooldownWithoutEscapingToTheCaller() {
        RecordingStore store = new RecordingStore();
        store.failWrites = true;
        LiteratureCacheService cache = cache(store, new MutableClock());

        cache.write("key", CacheValueKind.OPENALEX_SEARCH, "value", Duration.ofMinutes(1));
        assertThat(cache.read("key", CacheValueKind.OPENALEX_SEARCH, String.class)).isEmpty();

        assertThat(store.writeCount).isEqualTo(1);
        assertThat(store.readCount).isZero();
    }

    @Test
    void cacheConfigurationRejectsInvalidBoundsAndPrefixes() {
        LiteratureCacheProperties properties = new LiteratureCacheProperties();

        assertThatThrownBy(() -> properties.setKeyPrefix("Private Prefix"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setOpenalexTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setCrossrefTtl(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setNotFoundTtl(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> properties.setFailureCooldown(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxPayloadBytes(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static LiteratureCacheService cache(RecordingStore store, Clock clock) {
        LiteratureCacheProperties properties = new LiteratureCacheProperties();
        properties.setEnabled(true);
        return new LiteratureCacheService(properties, store, new ObjectMapper(), clock);
    }

    public static final class RecordingStore implements CacheStore {
        public final HashMap<String, String> values = new HashMap<>();
        public final HashMap<String, Duration> ttls = new HashMap<>();
        public final java.util.List<String> deleted = new java.util.ArrayList<>();
        public int readCount;
        public int writeCount;
        public boolean failReads;
        public boolean failWrites;

        @Override
        public Optional<String> get(String key) {
            readCount++;
            if (failReads) throw new IllegalStateException("Redis unavailable");
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            writeCount++;
            if (failWrites) throw new IllegalStateException("Redis unavailable");
            values.put(key, value);
            ttls.put(key, ttl);
        }

        @Override
        public void delete(String key) {
            deleted.add(key);
            values.remove(key);
        }
    }

    public static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-06T00:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        public void advance(Duration duration) { now = now.plus(duration); }
    }
}
