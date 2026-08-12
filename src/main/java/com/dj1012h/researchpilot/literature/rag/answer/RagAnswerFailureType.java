package com.dj1012h.researchpilot.literature.rag.answer;

/** Stable, low-cardinality failure semantics for the answer boundary. */
public enum RagAnswerFailureType {
    RAG_ANSWER_DISABLED,
    RAG_QUESTION_INVALID,
    RAG_RETRIEVAL_FAILED,
    RAG_INSUFFICIENT_EVIDENCE,
    RAG_GENERATION_UNAVAILABLE,
    RAG_ANSWER_OUTPUT_INVALID,
    RAG_ANSWER_VALIDATION_FAILED,
    RAG_ANSWER_DEADLINE_EXCEEDED,
    RAG_ANSWER_FAILED
}
