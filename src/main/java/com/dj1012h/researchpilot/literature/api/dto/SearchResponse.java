package com.dj1012h.researchpilot.literature.api.dto;

import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.VerificationResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Synchronous response contract for a completed literature search.
 *
 * <p>The papers list contains only results eligible for formal output. A search
 * that executes successfully but yields no eligible paper uses
 * {@link SearchStatus#NO_VERIFIED_RESULTS} and an empty list.</p>
 */
public record SearchResponse(
        UUID taskId,
        SearchStatus status,
        SearchPlan plan,
        int candidateCount,
        int deduplicatedCount,
        VerificationSummary verificationSummary,
        List<PaperResult> papers,
        ReviewResponse review,
        PublicTerminationReason terminationReason,
        String message,
        long elapsedMs,
        Instant completedAt
) {

    public SearchResponse {
        taskId = Objects.requireNonNull(taskId, "taskId 不能为空");
        status = Objects.requireNonNull(status, "status 不能为空");
        plan = Objects.requireNonNull(plan, "plan 不能为空");
        verificationSummary = Objects.requireNonNull(verificationSummary, "verificationSummary 不能为空");
        papers = List.copyOf(Objects.requireNonNull(papers, "papers 不能为空"));
        review = Objects.requireNonNull(review, "review must not be null");
        terminationReason = Objects.requireNonNull(terminationReason, "terminationReason must not be null");
        message = requireText(message, "message");
        completedAt = Objects.requireNonNull(completedAt, "completedAt 不能为空");

        if (candidateCount < 0 || deduplicatedCount < 0) {
            throw new IllegalArgumentException("候选数量不能小于 0");
        }
        if (deduplicatedCount > candidateCount) {
            throw new IllegalArgumentException("deduplicatedCount 不能大于 candidateCount");
        }
        if (verificationSummary.totalCount() != deduplicatedCount) {
            throw new IllegalArgumentException("核验统计总数必须等于 deduplicatedCount");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs 不能小于 0");
        }
        if (status == SearchStatus.NO_VERIFIED_RESULTS
                && (!papers.isEmpty()
                || verificationSummary.verifiedCount() > 0)) {
            throw new IllegalArgumentException("NO_VERIFIED_RESULTS 不能包含已核验的正式结果");
        }
    }

    public record PaperResult(
            PaperDTO paper,
            double relevanceScore,
            VerificationResult verification
    ) {
        public PaperResult {
            paper = Objects.requireNonNull(paper, "paper 不能为空");
            verification = Objects.requireNonNull(verification, "verification 不能为空");
            if (paper.doi() == null || paper.doi().isBlank()) {
                throw new IllegalArgumentException("正式返回的论文必须包含标准化 DOI");
            }
            if (verification.status() != VerificationResult.VerificationStatus.VERIFIED) {
                throw new IllegalArgumentException("正式返回的论文必须达到最低核验标准");
            }
            if (relevanceScore < 0.0 || relevanceScore > 1.0 || Double.isNaN(relevanceScore)) {
                throw new IllegalArgumentException("relevanceScore 必须在 0 到 1 之间");
            }
        }
    }

    public record VerificationSummary(
            int verifiedCount,
            int partiallyVerifiedCount,
            int unverifiedCount,
            int rejectedCount
    ) {
        public VerificationSummary {
            if (verifiedCount < 0
                    || partiallyVerifiedCount < 0
                    || unverifiedCount < 0
                    || rejectedCount < 0) {
                throw new IllegalArgumentException("核验统计数量不能小于 0");
            }
        }

        public int totalCount() {
            return verifiedCount + partiallyVerifiedCount + unverifiedCount + rejectedCount;
        }
    }

    public enum SearchStatus {
        COMPLETED,
        PARTIAL_SUCCESS,
        NO_VERIFIED_RESULTS
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
