package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.ReviewProperties;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Ordered boundary: syntax, schema, strict DTO, business rules, then citation ownership. */
@Component
public class ReviewDraftValidationPipeline {

    private final StructuredOutputMapper structuredOutputMapper;
    private final ReviewDraftSchemaValidator schemaValidator;
    private final ReviewDraftMapper draftMapper;
    private final ReviewDraftBusinessValidator businessValidator;
    private final CitationGuard citationGuard;
    private final int maxRawDraftLength;

    public ReviewDraftValidationPipeline(
            StructuredOutputMapper structuredOutputMapper,
            ReviewDraftSchemaValidator schemaValidator,
            ReviewDraftMapper draftMapper,
            ReviewDraftBusinessValidator businessValidator,
            CitationGuard citationGuard,
            ReviewProperties properties
    ) {
        this.structuredOutputMapper = Objects.requireNonNull(
                structuredOutputMapper, "structuredOutputMapper must not be null");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator must not be null");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper must not be null");
        this.businessValidator = Objects.requireNonNull(
                businessValidator, "businessValidator must not be null");
        this.citationGuard = Objects.requireNonNull(citationGuard, "citationGuard must not be null");
        this.maxRawDraftLength = Objects.requireNonNull(
                properties, "properties must not be null").getMaxRawDraftLength();
    }

    public ValidatedReview validate(UntrustedReviewDraft untrustedDraft, ReviewInput input) {
        Objects.requireNonNull(untrustedDraft, "untrustedDraft must not be null");
        Objects.requireNonNull(input, "input must not be null");
        JsonNode syntaxChecked = validateSyntax(untrustedDraft.rawContent());
        JsonNode schemaChecked = schemaValidator.validate(syntaxChecked);
        ReviewDraft draft = draftMapper.map(schemaChecked);
        ReviewDraft businessChecked = businessValidator.validate(draft, input);
        return citationGuard.validate(businessChecked, input);
    }

    private JsonNode validateSyntax(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw syntaxFailure("EMPTY_MODEL_OUTPUT", true);
        }
        if (rawOutput.length() > maxRawDraftLength) {
            throw syntaxFailure("MODEL_OUTPUT_TOO_LARGE", false);
        }
        try {
            JsonNode root = structuredOutputMapper.readTree(rawOutput);
            if (root == null || !root.isObject()) {
                throw syntaxFailure("JSON_ROOT_NOT_OBJECT", true);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw syntaxFailure("INVALID_JSON_SYNTAX", true);
        }
    }

    private ReviewDraftValidationException syntaxFailure(String code, boolean retryable) {
        return new ReviewDraftValidationException(
                ReviewValidationStage.JSON_SYNTAX,
                List.of(new ReviewValidationIssue(code, "$", retryable))
        );
    }
}
