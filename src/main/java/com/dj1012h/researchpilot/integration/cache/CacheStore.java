package com.dj1012h.researchpilot.integration.cache;

import java.time.Duration;
import java.util.Optional;

/** Narrow Redis boundary so cache-aside behavior remains independently testable. */
public interface CacheStore {
    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);

    void delete(String key);
}
