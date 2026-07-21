package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** Local, process-scoped concurrency and request-rate control. */
@Component
public class CrossrefRequestGate {

    private final Semaphore permits;
    private final long intervalMillis;
    private final Clock clock;
    private final CrossrefSleeper sleeper;
    private long nextAllowedAtMillis;

    @Autowired
    public CrossrefRequestGate(CrossrefProperties properties) {
        this(properties, Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()));
    }

    public CrossrefRequestGate(CrossrefProperties properties, Clock clock, CrossrefSleeper sleeper) {
        Objects.requireNonNull(properties, "properties 不能为空");
        this.permits = new Semaphore(properties.getMaxConcurrency(), true);
        this.intervalMillis = Math.max(1, (long) Math.ceil(1000.0 / properties.getRequestsPerSecond()));
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper 不能为空");
    }

    public <T> T execute(CheckedSupplier<T> request) {
        acquirePermit();
        try {
            awaitRateSlot();
            return request.get();
        } finally {
            permits.release();
        }
    }

    private void acquirePermit() {
        try {
            permits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossrefApiException(CrossrefFailureType.INTERRUPTED, "Crossref 请求被中断");
        }
    }

    private void awaitRateSlot() {
        long waitMillis;
        synchronized (this) {
            long now = clock.millis();
            long slot = Math.max(now, nextAllowedAtMillis);
            nextAllowedAtMillis = slot + intervalMillis;
            waitMillis = slot - now;
        }
        if (waitMillis <= 0) {
            return;
        }
        try {
            sleeper.sleep(Duration.ofMillis(waitMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CrossrefApiException(CrossrefFailureType.INTERRUPTED, "Crossref 限流等待被中断");
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get();
    }
}
