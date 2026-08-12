package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagRetrieval;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Converts validated text and trusted evidence into the bounded public DTO. */
@Component
public class RagAnswerResponseAssembler {

    public ResearchAnswerResponse success(
            UUID requestId,
            ValidatedRagAnswer validated,
            RagAnswerInput input,
            RagAnswerRetrievalSummary summary,
            long elapsedMs,
            int modelCallCount,
            int repairCount
    ) {
        Map<String, RagAnswerEvidence> evidenceById = input.evidence().stream()
                .collect(Collectors.toMap(RagAnswerEvidence::citationId, Function.identity(), (left, right) -> left));
        List<String> firstAppearanceIds = validated.statements().stream()
                .flatMap(statement -> statement.citationIds().stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        List<RagAnswerCitation> citations = firstAppearanceIds.stream()
                .map(evidenceById::get)
                .map(RagAnswerEvidence::toPublicCitation)
                .toList();
        String answer = validated.statements().stream()
                .map(RagAnswerStatement::text)
                .collect(Collectors.joining("\n"));
        return new ResearchAnswerResponse(
                requestId,
                RagAnswerStatus.SUCCESS,
                answer,
                citations,
                summary,
                false,
                "回答仅基于本次请求准入的 ABSTRACT 证据；CitationGuard 仅验证引用格式、存在性和本次证据归属，不证明全文事实或语义蕴含。",
                elapsedMs,
                new RagAnswerDiagnostics(null, modelCallCount, repairCount, citations.size()));
    }

    public ResearchAnswerResponse insufficient(
            UUID requestId,
            RagAnswerRetrievalSummary summary,
            long elapsedMs
    ) {
        return new ResearchAnswerResponse(
                requestId,
                RagAnswerStatus.INSUFFICIENT_EVIDENCE,
                "",
                List.of(),
                summary,
                true,
                "没有经过 MySQL 再准入的 ABSTRACT 证据，未调用生成模型。",
                elapsedMs,
                new RagAnswerDiagnostics(RagAnswerFailureType.RAG_INSUFFICIENT_EVIDENCE.name(), 0, 0, 0));
    }

    public ResearchAnswerResponse failed(
            UUID requestId,
            RagAnswerFailureType failureType,
            RagAnswerRetrievalSummary summary,
            long elapsedMs,
            int modelCallCount,
            int repairCount
    ) {
        return new ResearchAnswerResponse(
                requestId,
                RagAnswerStatus.FAILED,
                "",
                List.of(),
                summary,
                false,
                message(failureType),
                elapsedMs,
                new RagAnswerDiagnostics(failureType.name(), modelCallCount, repairCount, 0));
    }

    private String message(RagAnswerFailureType failureType) {
        return switch (failureType) {
            case RAG_ANSWER_DISABLED -> "可信 RAG 问答当前未启用。";
            case RAG_QUESTION_INVALID -> "问题或筛选条件不符合服务端边界。";
            case RAG_RETRIEVAL_FAILED -> "可信检索依赖当前不可用，未生成回答。";
            case RAG_GENERATION_UNAVAILABLE -> "生成模型当前不可用，未发布回答。";
            case RAG_ANSWER_OUTPUT_INVALID -> "生成输出无法安全解析，未发布回答。";
            case RAG_ANSWER_VALIDATION_FAILED -> "生成输出未通过安全验证，未发布回答。";
            case RAG_ANSWER_DEADLINE_EXCEEDED -> "可信 RAG 问答已超过本次请求时限，未发布回答。";
            case RAG_ANSWER_FAILED -> "可信 RAG 问答失败，未发布回答。";
            case RAG_INSUFFICIENT_EVIDENCE -> "没有足够的可信摘要证据。";
        };
    }
}
