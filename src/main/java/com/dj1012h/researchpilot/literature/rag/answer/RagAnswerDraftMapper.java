package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RagAnswerDraftMapper {
    private final StructuredOutputMapper mapper;

    public RagAnswerDraftMapper(StructuredOutputMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public RagAnswerDraft map(JsonNode root) {
        try {
            return mapper.treeToValue(root, RagAnswerDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new RagAnswerValidationException(
                    RagAnswerValidationStage.DTO_MAPPING,
                    List.of(new RagAnswerValidationIssue("DTO_MAPPING_FAILED", "$", true)));
        }
    }
}
