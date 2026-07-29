package com.dj1012h.researchpilot.literature.review;

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
public class ReviewDraftSchemaValidator {

    public static final String SCHEMA_VERSION = "evidence-review-draft-v1";

    private final Schema schema;

    public ReviewDraftSchemaValidator() {
        try {
            SchemaRegistry registry =
                    SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(
                    new ClassPathResource("schema/" + SCHEMA_VERSION + ".schema.json").getInputStream(),
                    InputFormat.JSON
            );
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("unable to load evidence review draft schema", exception);
        }
    }

    public JsonNode validate(JsonNode root) {
        List<com.networknt.schema.Error> errors = schema.validate(root);
        if (errors.isEmpty()) {
            return root;
        }
        List<ReviewValidationIssue> issues = errors.stream()
                .sorted(Comparator
                        .comparing((com.networknt.schema.Error error) ->
                                path(error.getInstanceLocation().toString()))
                        .thenComparing(com.networknt.schema.Error::getKeyword))
                .map(this::toIssue)
                .toList();
        throw new ReviewDraftValidationException(ReviewValidationStage.JSON_SCHEMA, issues);
    }

    private ReviewValidationIssue toIssue(com.networknt.schema.Error error) {
        String keyword = error.getKeyword();
        String code = switch (keyword) {
            case "required" -> "MISSING_REQUIRED_FIELD";
            case "type" -> "INVALID_FIELD_TYPE";
            case "enum" -> "INVALID_ENUM_VALUE";
            case "additionalProperties" -> "ADDITIONAL_PROPERTY_NOT_ALLOWED";
            case "maxItems" -> "TOO_MANY_ITEMS";
            case "minItems" -> "TOO_FEW_ITEMS";
            case "maxLength" -> "TEXT_LIMIT_EXCEEDED";
            case "minLength" -> "EMPTY_TEXT";
            case "pattern" -> "MALFORMED_CITATION_ID";
            case "uniqueItems" -> "DUPLICATE_CITATION_ID";
            default -> "SCHEMA_VALIDATION_FAILED";
        };
        return new ReviewValidationIssue(
                code,
                path(error.getInstanceLocation().toString()),
                true
        );
    }

    private static String path(String value) {
        return value == null || value.isBlank() ? "$" : "$" + value;
    }
}
