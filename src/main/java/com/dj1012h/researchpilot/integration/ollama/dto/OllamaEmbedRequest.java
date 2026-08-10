package com.dj1012h.researchpilot.integration.ollama.dto;

import java.util.List;
import java.util.Objects;

/** Ollama-specific /api/embed request DTO; it must not cross the adapter boundary. */
public record OllamaEmbedRequest(String model, List<String> input) {

    public OllamaEmbedRequest {
        model = Objects.requireNonNull(model, "model must not be null");
        input = List.copyOf(Objects.requireNonNull(input, "input must not be null"));
    }
}
