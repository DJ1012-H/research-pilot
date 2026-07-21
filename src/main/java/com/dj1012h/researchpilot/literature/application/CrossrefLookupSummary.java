package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;

import java.util.List;
import java.util.Objects;

/** Internal-only outcome of enriching OpenAlex candidates with Crossref metadata. */
public record CrossrefLookupSummary(
        int doiEligibleCount,
        int attemptedCount,
        int foundCount,
        int notFoundCount,
        int failedCount,
        int skippedByLimitCount,
        boolean crossrefEnabled,
        boolean sourceAvailable,
        List<CrossrefWorkMetadata> foundMetadata
) {
    public CrossrefLookupSummary {
        if (doiEligibleCount < 0 || attemptedCount < 0 || foundCount < 0 || notFoundCount < 0
                || failedCount < 0 || skippedByLimitCount < 0) {
            throw new IllegalArgumentException("Crossref 查询统计不能小于 0");
        }
        if (attemptedCount != foundCount + notFoundCount + failedCount) {
            throw new IllegalArgumentException("Crossref 尝试次数必须等于结果统计之和");
        }
        foundMetadata = List.copyOf(Objects.requireNonNull(foundMetadata, "foundMetadata 不能为空"));
        if (foundMetadata.size() != foundCount) {
            throw new IllegalArgumentException("foundMetadata 数量必须等于 foundCount");
        }
    }
}
