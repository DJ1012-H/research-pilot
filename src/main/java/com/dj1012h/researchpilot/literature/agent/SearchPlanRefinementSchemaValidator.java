package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import com.dj1012h.researchpilot.literature.validation.ValidationStage;
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
public class SearchPlanRefinementSchemaValidator {

    private final Schema schema;

    public SearchPlanRefinementSchemaValidator() {
        ClassPathResource resource =
                new ClassPathResource("schema/search-plan-refinement-v1.schema.json");
        try {
            SchemaRegistry registry =
                    SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(resource.getInputStream(), InputFormat.JSON);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "unable to load search-plan refinement schema",
                    exception
            );
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
        String code = switch (error.getKeyword()) {
            case "required" -> "MISSING_REQUIRED_FIELD";
            case "type" -> "INVALID_FIELD_TYPE";
            case "additionalProperties" -> "ADDITIONAL_PROPERTY_NOT_ALLOWED";
            default -> "SCHEMA_VALIDATION_FAILED";
        };
        return new ValidationIssue(
                code,
                path(error.getInstanceLocation().toString()),
                "refinement JSON Schema validation failed: " + error.getKeyword(),
                true
        );
    }

    private static String path(String value) {
        return value == null || value.isBlank() ? "$" : "$" + value;
    }
}
