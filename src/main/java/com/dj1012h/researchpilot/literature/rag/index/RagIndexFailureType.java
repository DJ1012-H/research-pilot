package com.dj1012h.researchpilot.literature.rag.index;

public enum RagIndexFailureType {
    DISABLED,
    TRANSPORT_FAILURE,
    HTTP_FAILURE,
    INVALID_RESPONSE,
    COLLECTION_MISMATCH,
    POINT_MISMATCH
}
