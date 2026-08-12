package com.dj1012h.researchpilot.literature.rag.retrieval;

/** Stable, low-cardinality failure codes for the Day 4 retrieval boundary. */
public enum RagRetrievalFailureType {
    RAG_RETRIEVAL_DISABLED,
    RAG_ACTIVE_VERSION_MISSING,
    RAG_QUERY_INVALID,
    RAG_EMBEDDING_UNAVAILABLE,
    RAG_EMBEDDING_DIMENSION_MISMATCH,
    RAG_INDEX_UNAVAILABLE,
    RAG_INDEX_RESPONSE_INVALID,
    RAG_INDEX_VERSION_MISMATCH,
    RAG_TRUSTED_SOURCE_UNAVAILABLE,
    RAG_NO_TRUSTED_RESULTS,
    RAG_RETRIEVAL_FAILED
}
