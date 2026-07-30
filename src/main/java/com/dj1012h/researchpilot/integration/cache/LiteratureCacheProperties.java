package com.dj1012h.researchpilot.integration.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/** Server-owned bounds for the optional external-literature API cache. */
@Component
@ConfigurationProperties(prefix = "app.literature.cache")
public class LiteratureCacheProperties {

    private boolean enabled = false;
    private String keyPrefix = "research-pilot:literature:v1";
    private Duration openalexTtl = Duration.ofMinutes(15);
    private Duration crossrefTtl = Duration.ofHours(24);
    private Duration notFoundTtl = Duration.ofMinutes(5);
    private Duration failureCooldown = Duration.ofSeconds(30);
    private int maxPayloadBytes = 2 * 1024 * 1024;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9:_-]*")) {
            throw new IllegalArgumentException("key-prefix must contain only lowercase letters, digits, colon, dash, or underscore");
        }
        keyPrefix = value;
    }
    public Duration getOpenalexTtl() { return openalexTtl; }
    public void setOpenalexTtl(Duration value) { openalexTtl = positive(value, "openalex-ttl"); }
    public Duration getCrossrefTtl() { return crossrefTtl; }
    public void setCrossrefTtl(Duration value) { crossrefTtl = positive(value, "crossref-ttl"); }
    public Duration getNotFoundTtl() { return notFoundTtl; }
    public void setNotFoundTtl(Duration value) { notFoundTtl = positive(value, "not-found-ttl"); }
    public Duration getFailureCooldown() { return failureCooldown; }
    public void setFailureCooldown(Duration value) { failureCooldown = positive(value, "failure-cooldown"); }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public void setMaxPayloadBytes(int value) {
        if (value < 1) throw new IllegalArgumentException("max-payload-bytes must be positive");
        maxPayloadBytes = value;
    }

    private Duration positive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
