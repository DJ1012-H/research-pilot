package com.dj1012h.researchpilot.literature.rag.answer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public fail-closed response. Internal drafts and complete segments are excluded. */
public record ResearchAnswerResponse(
        UUID requestId,
        RagAnswerStatus status,
        String answer,
        List<RagAnswerCitation> citations,
        RagAnswerRetrievalSummary retrievalSummary,
        boolean insufficientEvidence,
        String message,
        long elapsedMs,
        RagAnswerDiagnostics diagnostics
) {
    public ResearchAnswerResponse {
        requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        answer = Objects.requireNonNull(answer, "answer must not be null");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations must not be null"));
        retrievalSummary = Objects.requireNonNull(retrievalSummary, "retrievalSummary must not be null");
        message = Objects.requireNonNull(message, "message must not be null");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        if (elapsedMs < 0) throw new IllegalArgumentException("elapsedMs must not be negative");
        if (status != RagAnswerStatus.SUCCESS && (!answer.isEmpty() || !citations.isEmpty())) {
            throw new IllegalArgumentException("non-success answer must not publish text or citations");
        }
        if (status == RagAnswerStatus.INSUFFICIENT_EVIDENCE && !insufficientEvidence) {
            throw new IllegalArgumentException("insufficient status must set insufficientEvidence");
        }
        if (status == RagAnswerStatus.SUCCESS && insufficientEvidence) {
            throw new IllegalArgumentException("success must not set insufficientEvidence");
        }
    }
}
