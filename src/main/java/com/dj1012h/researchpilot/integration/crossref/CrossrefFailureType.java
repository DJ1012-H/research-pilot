package com.dj1012h.researchpilot.integration.crossref;

public enum CrossrefFailureType {
    DISABLED,
    MAILTO_MISSING,
    USER_AGENT_MISSING,
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    TIMEOUT,
    TRANSPORT_ERROR,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    INTERRUPTED
}
