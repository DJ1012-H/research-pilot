package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Fixed five-stage boundary from raw model text to a trusted agent action. */
@Component
public class SearchActionValidationPipeline {

    private final StructuredOutputMapper structuredOutputMapper;
    private final SearchActionSchemaValidator schemaValidator;
    private final SearchActionDraftMapper draftMapper;
    private final SearchActionBusinessValidator businessValidator;
    private final SearchActionSecurityValidator securityValidator;

    public SearchActionValidationPipeline(StructuredOutputMapper structuredOutputMapper,
                                          SearchActionSchemaValidator schemaValidator,
                                          SearchActionDraftMapper draftMapper,
                                          SearchActionBusinessValidator businessValidator,
                                          SearchActionSecurityValidator securityValidator) {
        this.structuredOutputMapper = structuredOutputMapper;
        this.schemaValidator = schemaValidator;
        this.draftMapper = draftMapper;
        this.businessValidator = businessValidator;
        this.securityValidator = securityValidator;
    }

    public AgentAction validate(String rawModelOutput, Set<AgentAction> allowedActions) {
        JsonNode syntaxChecked = validateSyntax(rawModelOutput);
        JsonNode schemaChecked = schemaValidator.validate(syntaxChecked);
        SearchActionDraft draft = draftMapper.map(schemaChecked);
        AgentAction action = businessValidator.validate(draft);
        return securityValidator.validate(action, allowedActions);
    }

    private JsonNode validateSyntax(String rawModelOutput) {
        if (rawModelOutput == null || rawModelOutput.isBlank()) throw syntaxFailure("ACTION_JSON_INVALID");
        try {
            JsonNode root = structuredOutputMapper.readTree(rawModelOutput);
            if (root == null || !root.isObject()) throw syntaxFailure("ACTION_JSON_INVALID");
            return root;
        } catch (JsonProcessingException exception) {
            throw syntaxFailure("ACTION_JSON_INVALID");
        }
    }

    private SearchActionValidationException syntaxFailure(String code) {
        return new SearchActionValidationException(SearchActionValidationStage.JSON_SYNTAX,
                List.of(new SearchActionValidationIssue(code, "$", true)));
    }
}
