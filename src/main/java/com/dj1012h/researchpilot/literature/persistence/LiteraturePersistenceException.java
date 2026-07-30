package com.dj1012h.researchpilot.literature.persistence;

/** Explicit infrastructure failure; callers must not turn it into a successful response. */
public class LiteraturePersistenceException extends RuntimeException {
    public LiteraturePersistenceException(String message) { super(message); }
    public LiteraturePersistenceException(String message, Throwable cause) { super(message, cause); }
}
