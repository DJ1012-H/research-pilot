package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.literature.validation.JsonSyntaxValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Strict syntax, schema and DTO boundary for the untrusted refinement proposal. */
@Component
public class SearchPlanRefinementDraftValidationPipeline {

    private final JsonSyntaxValidator syntaxValidator;
    private final SearchPlanRefinementSchemaValidator schemaValidator;
    private final SearchPlanRefinementDraftMapper draftMapper;

    public SearchPlanRefinementDraftValidationPipeline(
            JsonSyntaxValidator syntaxValidator,
            SearchPlanRefinementSchemaValidator schemaValidator,
            SearchPlanRefinementDraftMapper draftMapper
    ) {
        this.syntaxValidator = Objects.requireNonNull(
                syntaxValidator,
                "syntaxValidator must not be null"
        );
        this.schemaValidator = Objects.requireNonNull(
                schemaValidator,
                "schemaValidator must not be null"
        );
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper must not be null");
    }

    public SearchPlanRefinementDraft validate(String rawModelOutput) {
        JsonNode syntaxChecked = syntaxValidator.validate(rawModelOutput);
        JsonNode schemaChecked = schemaValidator.validate(syntaxChecked);
        return draftMapper.map(schemaChecked);
    }
}
