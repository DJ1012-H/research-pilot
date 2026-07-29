package com.dj1012h.researchpilot.literature.review;

import java.util.Objects;

/** Raw model output; it is not a verified draft and must not reach public output. */
public record UntrustedReviewDraft(String rawContent) {
    public UntrustedReviewDraft {
        rawContent = Objects.requireNonNull(rawContent, "rawContent must not be null");
    }
}
