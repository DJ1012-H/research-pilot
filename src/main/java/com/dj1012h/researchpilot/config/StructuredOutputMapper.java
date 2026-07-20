package com.dj1012h.researchpilot.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Narrow gateway to the strict mapper used for untrusted LLM output.
 *
 * <p>This type intentionally does not extend {@link ObjectMapper}, so exposing
 * it as a Spring bean cannot replace Spring Boot's MVC mapper.</p>
 */
public final class StructuredOutputMapper {

    private final ObjectMapper objectMapper;

    public StructuredOutputMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public JsonNode readTree(String json) throws JsonProcessingException {
        return objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(json);
    }

    public <T> T treeToValue(JsonNode root, Class<T> targetType) throws JsonProcessingException {
        return objectMapper.treeToValue(root, targetType);
    }
}
