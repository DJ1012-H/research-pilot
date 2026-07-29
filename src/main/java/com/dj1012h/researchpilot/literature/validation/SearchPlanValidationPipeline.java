package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

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
        return validateWithOrigins(context, rawModelOutput).plan();
    }

    public SearchPlanValidationResult validateWithOrigins(
            SearchPlanGenerationContext context,
            String rawModelOutput
    ) {
        JsonNode syntaxChecked = syntaxValidator.validate(rawModelOutput);
        JsonNode schemaChecked = schemaValidator.validate(syntaxChecked);
        SearchPlanDraft draft = draftMapper.map(schemaChecked);
        SearchPlanValidationResult result = businessValidator.validateWithOrigins(context, draft);
        SearchPlan trustedPlan = securityValidator.validate(result.plan());
        return new SearchPlanValidationResult(trustedPlan, result.origins());
    }

    /**
     * Runs the same five validation layers for a refinement and then enforces
     * that every non-refinable field still equals the current trusted plan.
     */
    public SearchPlanValidationResult revalidate(
            SearchPlanGenerationContext originalContext,
            String mergedDraftJson,
            SearchPlanValidationResult current
    ) {
        Objects.requireNonNull(current, "current must not be null");
        SearchPlanValidationResult revalidated =
                validateWithOrigins(originalContext, mergedDraftJson);
        assertFrozenFields(current.plan(), revalidated.plan());
        return new SearchPlanValidationResult(revalidated.plan(), current.origins());
    }

    private void assertFrozenFields(SearchPlan current, SearchPlan refined) {
        boolean changed = !current.originalQuery().equals(refined.originalQuery())
                || !current.topic().equals(refined.topic())
                || !current.languages().equals(refined.languages())
                || !current.publicationTypes().equals(refined.publicationTypes())
                || current.sort() != refined.sort()
                || current.fromYear() != refined.fromYear()
                || current.toYear() != refined.toYear()
                || current.resultLimit() != refined.resultLimit()
                || current.candidateLimit() != refined.candidateLimit();
        if (changed) {
            throw new SearchPlanValidationException(
                    ValidationStage.BUSINESS_RULE,
                    List.of(new ValidationIssue(
                            "FROZEN_CONSTRAINT_CHANGED",
                            "$",
                            "refinement changed a frozen trusted-plan field",
                            false
                    ))
            );
        }
    }
}
