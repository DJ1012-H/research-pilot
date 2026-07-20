package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Component
public class SearchPlanSchemaValidator {

    private final Schema schema;

    public SearchPlanSchemaValidator(AiProperties aiProperties) {
        String schemaVersion = aiProperties.getStructuredOutput().getSchemaVersion();
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalStateException("structured output schema version 不能为空");
        }
        ClassPathResource resource =
                new ClassPathResource("schema/" + schemaVersion + ".schema.json");
        try {
            SchemaRegistry registry =
                    SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(resource.getInputStream(), InputFormat.JSON);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("无法加载结构化输出 Schema: " + schemaVersion, exception);
        }
    }

    public JsonNode validate(JsonNode root) {
        List<com.networknt.schema.Error> errors = schema.validate(root);
        if (errors.isEmpty()) {
            return root;
        }

        List<ValidationIssue> issues = errors.stream()
                .sorted(Comparator
                        .comparing((com.networknt.schema.Error error) ->
                                path(error.getInstanceLocation().toString()))
                        .thenComparing(com.networknt.schema.Error::getKeyword))
                .map(this::toIssue)
                .toList();
        throw new SearchPlanValidationException(ValidationStage.JSON_SCHEMA, issues);
    }

    private ValidationIssue toIssue(com.networknt.schema.Error error) {
        String keyword = error.getKeyword();
        String code = switch (keyword) {
            case "required" -> "MISSING_REQUIRED_FIELD";
            case "type" -> "INVALID_FIELD_TYPE";
            case "enum" -> "INVALID_ENUM_VALUE";
            case "additionalProperties" -> "ADDITIONAL_PROPERTY_NOT_ALLOWED";
            default -> "SCHEMA_VALIDATION_FAILED";
        };
        return new ValidationIssue(
                code,
                path(error.getInstanceLocation().toString()),
                "JSON Schema 规则校验失败: " + keyword,
                true
        );
    }

    private static String path(String value) {
        return value == null || value.isBlank() ? "$" : "$" + value;
    }
}
