package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchActionDraftMapper {

    private final StructuredOutputMapper structuredOutputMapper;

    public SearchActionDraftMapper(StructuredOutputMapper structuredOutputMapper) {
        this.structuredOutputMapper = structuredOutputMapper;
    }

    public SearchActionDraft map(JsonNode root) {
        try {
            return structuredOutputMapper.treeToValue(root, SearchActionDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw failure("ACTION_MAPPING_FAILED");
        }
    }

    private SearchActionValidationException failure(String code) {
        return new SearchActionValidationException(SearchActionValidationStage.DTO_MAPPING,
                List.of(new SearchActionValidationIssue(code, "$", true)));
    }
}
