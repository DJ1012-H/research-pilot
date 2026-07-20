package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JsonSyntaxValidator {

    private final StructuredOutputMapper structuredOutputMapper;
    private final int maxOutputLength;

    @Autowired
    public JsonSyntaxValidator(
            StructuredOutputMapper structuredOutputMapper,
            AiProperties aiProperties
    ) {
        this.structuredOutputMapper = structuredOutputMapper;
        this.maxOutputLength = aiProperties.getStructuredOutput().getMaxOutputLength();
        if (maxOutputLength < 1) {
            throw new IllegalStateException("structured output max length 必须大于 0");
        }
    }

    public JsonSyntaxValidator(
            @Qualifier(StructuredOutputConfiguration.OBJECT_MAPPER_BEAN) ObjectMapper objectMapper,
            AiProperties aiProperties
    ) {
        this(new StructuredOutputMapper(objectMapper), aiProperties);
    }

    public JsonNode validate(String rawModelOutput) {
        if (rawModelOutput == null || rawModelOutput.isBlank()) {
            throw failure("EMPTY_MODEL_OUTPUT", "模型没有返回 JSON", true);
        }
        if (rawModelOutput.length() > maxOutputLength) {
            throw failure("MODEL_OUTPUT_TOO_LARGE", "模型输出超过长度预算", false);
        }

        JsonNode root;
        try {
            root = structuredOutputMapper.readTree(rawModelOutput);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_JSON_SYNTAX", "模型输出不是严格 JSON", true);
        }
        if (root == null || !root.isObject()) {
            throw failure("JSON_ROOT_NOT_OBJECT", "JSON 根节点必须是对象", true);
        }
        return root;
    }

    private SearchPlanValidationException failure(String code, String message, boolean retryable) {
        return new SearchPlanValidationException(
                ValidationStage.JSON_SYNTAX,
                List.of(new ValidationIssue(code, "$", message, retryable))
        );
    }
}
