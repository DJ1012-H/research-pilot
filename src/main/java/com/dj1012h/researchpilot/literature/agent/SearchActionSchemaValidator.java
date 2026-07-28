package com.dj1012h.researchpilot.literature.agent;

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
public class SearchActionSchemaValidator {

    private final Schema schema;

    public SearchActionSchemaValidator() {
        try {
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(
                    new ClassPathResource("schemas/search-action-draft-v1.json").getInputStream(), InputFormat.JSON);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load search action schema", exception);
        }
    }

    public JsonNode validate(JsonNode root) {
        List<com.networknt.schema.Error> errors = schema.validate(root);
        if (errors.isEmpty()) return root;
        List<SearchActionValidationIssue> issues = errors.stream()
                .sorted(Comparator.comparing((com.networknt.schema.Error error) -> path(error.getInstanceLocation().toString()))
                        .thenComparing(com.networknt.schema.Error::getKeyword))
                .map(error -> new SearchActionValidationIssue(codeFor(error.getKeyword()),
                        path(error.getInstanceLocation().toString()), true))
                .toList();
        throw new SearchActionValidationException(SearchActionValidationStage.JSON_SCHEMA, issues);
    }

    private static String codeFor(String keyword) {
        return switch (keyword) {
            case "required" -> "MISSING_REQUIRED_FIELD";
            case "type" -> "INVALID_FIELD_TYPE";
            case "enum" -> "INVALID_ACTION";
            case "additionalProperties" -> "ADDITIONAL_PROPERTY_NOT_ALLOWED";
            default -> "ACTION_SCHEMA_INVALID";
        };
    }

    private static String path(String value) {
        return value == null || value.isBlank() ? "$" : "$" + value;
    }
}
