package com.dj1012h.researchpilot.integration.openalex;

public enum OpenAlexFailureType {
    DISABLED,
    API_KEY_MISSING,
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    TRANSPORT_ERROR
}
