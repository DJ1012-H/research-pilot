package com.dj1012h.researchpilot.integration.ollama.dto;

import java.util.List;

/** Minimal Ollama-specific /api/embed response DTO. */
public record OllamaEmbedResponse(List<List<Double>> embeddings) { }
