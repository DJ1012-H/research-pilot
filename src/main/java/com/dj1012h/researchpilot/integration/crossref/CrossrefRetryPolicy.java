package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
public class CrossrefRetryPolicy {

    private final CrossrefProperties properties;
    private final CrossrefSleeper sleeper;

    @Autowired
    public CrossrefRetryPolicy(CrossrefProperties properties) {
        this(properties, duration -> Thread.sleep(duration.toMillis()));
    }

    public CrossrefRetryPolicy(CrossrefProperties properties, CrossrefSleeper sleeper) {
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper 不能为空");
    }

    public boolean shouldRetry(CrossrefApiException exception, int completedRequests) {
        return completedRequests <= properties.getMaxRetries() && switch (exception.getFailureType()) {
            case RATE_LIMITED, SERVER_ERROR, TIMEOUT, TRANSPORT_ERROR -> true;
            default -> false;
        };
    }

    public void backoff(CrossrefApiException exception, int retryNumber) {
        Duration delay = exception.getRetryAfter();
        if (delay == null || delay.isNegative() || delay.isZero()) {
            long multiplier = 1L << Math.min(62, Math.max(0, retryNumber - 1));
            try {
                delay = properties.getInitialBackoff().multipliedBy(multiplier);
            } catch (ArithmeticException ignored) {
                delay = properties.getMaxBackoff();
            }
        }
        if (delay.compareTo(properties.getMaxBackoff()) > 0) {
            delay = properties.getMaxBackoff();
        }
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CrossrefApiException(CrossrefFailureType.INTERRUPTED, "Crossref 重试等待被中断");
        }
    }
}
