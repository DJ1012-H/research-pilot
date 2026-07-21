package com.dj1012h.researchpilot.integration.crossref;

import java.time.Duration;

@FunctionalInterface
public interface CrossrefSleeper {
    void sleep(Duration duration) throws InterruptedException;
}
