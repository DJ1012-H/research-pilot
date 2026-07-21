package com.dj1012h.researchpilot.integration.crossref;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefRequestGateAndRetryPolicyTest {

    @Test
    void shouldEnforceLocalRateWithoutRealSleeping() {
        CrossrefProperties properties = properties();
        MutableClock clock = new MutableClock();
        List<Duration> sleeps = new ArrayList<>();
        CrossrefRequestGate gate = new CrossrefRequestGate(properties, clock, duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        });

        gate.execute(() -> "first");
        gate.execute(() -> "second");
        gate.execute(() -> "third");

        assertThat(sleeps).containsExactly(Duration.ofMillis(200), Duration.ofMillis(200));
    }

    @Test
    void shouldReleasePermitAfterFailure() throws Exception {
        CrossrefProperties properties = properties();
        properties.setMaxConcurrency(1);
        CrossrefRequestGate gate = new CrossrefRequestGate(properties, Clock.systemUTC(), duration -> { });
        try {
            gate.execute(() -> { throw new CrossrefApiException(CrossrefFailureType.INVALID_RESPONSE, "test"); });
        } catch (CrossrefApiException ignored) {
        }
        CountDownLatch completed = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            gate.execute(() -> {
                completed.countDown();
                return null;
            });
        });
        thread.start();
        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();
        thread.join();
    }

    @Test
    void shouldUseExponentialBackoffWithMaximumCap() {
        CrossrefProperties properties = properties();
        properties.setInitialBackoff(Duration.ofMillis(250));
        properties.setMaxBackoff(Duration.ofMillis(500));
        List<Duration> sleeps = new ArrayList<>();
        CrossrefRetryPolicy policy = new CrossrefRetryPolicy(properties, sleeps::add);
        CrossrefApiException failure = new CrossrefApiException(CrossrefFailureType.SERVER_ERROR, "test");

        policy.backoff(failure, 1);
        policy.backoff(failure, 2);
        policy.backoff(failure, 3);

        assertThat(sleeps).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(500));
        assertThat(policy.shouldRetry(failure, 1)).isTrue();
        assertThat(policy.shouldRetry(failure, 2)).isTrue();
        assertThat(policy.shouldRetry(failure, 3)).isFalse();
    }

    private CrossrefProperties properties() {
        CrossrefProperties properties = new CrossrefProperties();
        properties.setRequestsPerSecond(5);
        properties.setMaxConcurrency(2);
        properties.setMaxRetries(2);
        return properties;
    }

    private static final class MutableClock extends Clock {
        private final AtomicInteger millis = new AtomicInteger();
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis()); }
        @Override public long millis() { return millis.get(); }
        void advance(Duration duration) { millis.addAndGet((int) duration.toMillis()); }
    }
}
