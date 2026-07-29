package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import com.dj1012h.researchpilot.literature.validation.ValidationStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SearchPlanRefinementDraftMapper {

    private final StructuredOutputMapper mapper;

    public SearchPlanRefinementDraftMapper(StructuredOutputMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public SearchPlanRefinementDraft map(JsonNode root) {
        try {
            return mapper.treeToValue(root, SearchPlanRefinementDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SearchPlanValidationException(
                    ValidationStage.DTO_MAPPING,
                    List.of(new ValidationIssue(
                            "DTO_MAPPING_FAILED",
                            "$",
                            "JSON cannot be mapped to SearchPlanRefinementDraft",
                            true
                    ))
            );
        }
    }
}
