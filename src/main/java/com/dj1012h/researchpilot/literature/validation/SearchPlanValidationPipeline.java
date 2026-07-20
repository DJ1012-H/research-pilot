package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * The single ordered entry point from raw model output to a trusted search plan.
 */
@Component
public class SearchPlanValidationPipeline {

    private final JsonSyntaxValidator syntaxValidator;
    private final SearchPlanSchemaValidator schemaValidator;
    private final SearchPlanDraftMapper draftMapper;
    private final SearchPlanBusinessValidator businessValidator;
    private final SearchPlanSecurityValidator securityValidator;

    public SearchPlanValidationPipeline(
            JsonSyntaxValidator syntaxValidator,
            SearchPlanSchemaValidator schemaValidator,
            SearchPlanDraftMapper draftMapper,
            SearchPlanBusinessValidator businessValidator,
            SearchPlanSecurityValidator securityValidator
    ) {
        this.syntaxValidator = syntaxValidator;
        this.schemaValidator = schemaValidator;
        this.draftMapper = draftMapper;
        this.businessValidator = businessValidator;
        this.securityValidator = securityValidator;
    }

    public SearchPlan validate(
            SearchPlanGenerationContext context,
            String rawModelOutput
    ) {
        JsonNode syntaxChecked = syntaxValidator.validate(rawModelOutput);
        JsonNode schemaChecked = schemaValidator.validate(syntaxChecked);
        SearchPlanDraft draft = draftMapper.map(schemaChecked);
        SearchPlan plan = businessValidator.validate(context, draft);
        return securityValidator.validate(plan);
    }
}
