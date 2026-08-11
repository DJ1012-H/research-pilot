package com.dj1012h.researchpilot.dto.response;

public record SystemStatusResponse(
        String application,
        DependencyStatusResponse mysql,
        DependencyStatusResponse redis,
        DependencyStatusResponse ollamaEmbedding,
        DependencyStatusResponse qdrant,
        boolean llmConfigured
) {
}
