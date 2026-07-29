package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ReviewDraftMapper {

    private final StructuredOutputMapper structuredOutputMapper;

    public ReviewDraftMapper(StructuredOutputMapper structuredOutputMapper) {
        this.structuredOutputMapper = Objects.requireNonNull(
                structuredOutputMapper, "structuredOutputMapper must not be null");
    }

    public ReviewDraft map(JsonNode root) {
        try {
            return structuredOutputMapper.treeToValue(root, ReviewDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ReviewDraftValidationException(
                    ReviewValidationStage.DTO_MAPPING,
                    List.of(new ReviewValidationIssue(
                            "DTO_MAPPING_FAILED", "$", true))
            );
        }
    }
}
