package com.dj1012h.researchpilot.literature.rag.embedding;

/** Stable fail-closed categories for an embedding provider boundary. */
public enum EmbeddingFailureType {
    DISABLED,
    INVALID_INPUT,
    HTTP_FAILURE,
    TRANSPORT_FAILURE,
    INVALID_RESPONSE,
    MISSING_EMBEDDINGS,
    EMPTY_VECTOR,
    VECTOR_COUNT_MISMATCH,
    MODEL_MISMATCH,
    DIMENSION_MISMATCH,
    NON_FINITE_VECTOR
}
