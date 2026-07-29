package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.api.dto.ReviewCitation;
import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.review.CitationId;
import com.dj1012h.researchpilot.literature.review.EvidencePaper;
import com.dj1012h.researchpilot.literature.review.ReviewInput;
import com.dj1012h.researchpilot.literature.review.ReviewOutcome;
import com.dj1012h.researchpilot.literature.review.ValidatedReview;
import com.dj1012h.researchpilot.literature.review.ValidatedReviewStatement;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Converts validated internal review data into the stable public contract. */
@Component
public class ReviewResponseAssembler {

    public ReviewResponse assemble(ReviewOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        return switch (outcome.status()) {
            case GENERATED -> generated(
                    outcome.validatedReview().orElseThrow(),
                    outcome.reviewInput().orElseThrow());
            case INSUFFICIENT_EVIDENCE -> degraded(
                    ReviewResponse.ReviewStatus.INSUFFICIENT_EVIDENCE,
                    insufficientEvidenceMessage(outcome));
            case INPUT_BUDGET_EXCEEDED -> degraded(
                    ReviewResponse.ReviewStatus.INPUT_BUDGET_EXCEEDED,
                    "证据在安全长度预算内不足，未生成综述。");
            case VALIDATION_FAILED -> degraded(
                    ReviewResponse.ReviewStatus.VALIDATION_FAILED,
                    "生成结果未通过结构或引用校验，已安全丢弃。");
            case GENERATION_UNAVAILABLE -> degraded(
                    ReviewResponse.ReviewStatus.GENERATION_UNAVAILABLE,
                    "综述生成服务当前不可用，已保留已核验论文结果。");
            case DEADLINE_EXCEEDED -> degraded(
                    ReviewResponse.ReviewStatus.DEADLINE_EXCEEDED,
                    "综述生成已达到执行时间上限，已安全终止。");
        };
    }

    private ReviewResponse generated(ValidatedReview review, ReviewInput input) {
        Map<CitationId, EvidencePaper> evidenceById = new LinkedHashMap<>();
        for (EvidencePaper paper : input.evidencePapers()) {
            evidenceById.put(paper.citationId(), paper);
        }

        Set<CitationId> citedIds = new LinkedHashSet<>();
        for (ValidatedReviewStatement statement : review.statements()) {
            citedIds.addAll(statement.citationIds());
        }
        List<ReviewCitation> citations = citedIds.stream()
                .map(id -> toPublicCitation(evidenceById.get(id)))
                .toList();

        return new ReviewResponse(
                ReviewResponse.ReviewStatus.GENERATED,
                renderSummary(review),
                citations,
                "综述已通过结构与引用映射校验；该校验不代表全文语义或事实核验。"
        );
    }

    private String renderSummary(ValidatedReview review) {
        return review.statements().stream()
                .map(statement -> statement.text() + " " + renderCitationMarkers(statement.citationIds()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElseThrow();
    }

    private String renderCitationMarkers(List<CitationId> citationIds) {
        return citationIds.stream()
                .map(id -> "[" + id.value() + "]")
                .reduce("", String::concat);
    }

    private ReviewCitation toPublicCitation(EvidencePaper paper) {
        Objects.requireNonNull(paper, "validated citation must resolve to evidence");
        return new ReviewCitation(
                paper.citationId().value(),
                paper.citationId().formalPaperPosition(),
                paper.normalizedDoi(),
                paper.title(),
                paper.authorDisplayNames(),
                paper.publicationYear(),
                paper.venue()
        );
    }

    private ReviewResponse degraded(ReviewResponse.ReviewStatus status, String message) {
        return new ReviewResponse(status, "", List.of(), message);
    }

    private String insufficientEvidenceMessage(ReviewOutcome outcome) {
        return outcome.failureCode()
                .filter("INSUFFICIENT_ABSTRACTS"::equals)
                .map(ignored -> "可用的已核验摘要证据不足，未生成综述。")
                .orElse("已核验的可信论文数量不足，未生成综述。");
    }
}
