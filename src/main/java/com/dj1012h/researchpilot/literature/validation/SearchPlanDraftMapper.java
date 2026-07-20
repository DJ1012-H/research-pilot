package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchPlanDraftMapper {

    private final StructuredOutputMapper structuredOutputMapper;

    @Autowired
    public SearchPlanDraftMapper(StructuredOutputMapper structuredOutputMapper) {
        this.structuredOutputMapper = structuredOutputMapper;
    }

    public SearchPlanDraftMapper(
            @Qualifier(StructuredOutputConfiguration.OBJECT_MAPPER_BEAN) ObjectMapper objectMapper
    ) {
        this(new StructuredOutputMapper(objectMapper));
    }

    public SearchPlanDraft map(JsonNode root) {
        try {
            return structuredOutputMapper.treeToValue(root, SearchPlanDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SearchPlanValidationException(
                    ValidationStage.DTO_MAPPING,
                    List.of(new ValidationIssue(
                            "DTO_MAPPING_FAILED",
                            "$",
                            "JSON 无法严格映射为 SearchPlanDraft",
                            true
                    ))
            );
        }
    }
}
