package com.dj1012h.researchpilot.literature.rag.answer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Ordered raw -> syntax -> schema -> DTO -> business -> citation validation. */
@Component
public class RagAnswerValidationPipeline {
    private final StructuredOutputMapper mapper;
    private final RagAnswerDraftSchemaValidator schemaValidator;
    private final RagAnswerDraftMapper draftMapper;
    private final RagAnswerBusinessValidator businessValidator;
    private final RagAnswerCitationGuard citationGuard;
    private final int maxRawDraftChars;

    public RagAnswerValidationPipeline(
            StructuredOutputMapper mapper,
            RagAnswerDraftSchemaValidator schemaValidator,
            RagAnswerDraftMapper draftMapper,
            RagAnswerBusinessValidator businessValidator,
            RagAnswerCitationGuard citationGuard,
            RagAnswerProperties properties
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator must not be null");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper must not be null");
        this.businessValidator = Objects.requireNonNull(businessValidator, "businessValidator must not be null");
        this.citationGuard = Objects.requireNonNull(citationGuard, "citationGuard must not be null");
        this.maxRawDraftChars = Objects.requireNonNull(properties, "properties must not be null").getMaxRawDraftChars();
    }

    public ValidatedRagAnswer validate(UntrustedRagAnswerDraft draft, RagAnswerInput input) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(input, "input must not be null");
        JsonNode syntax = validateSyntax(draft.rawContent());
        JsonNode schema = schemaValidator.validate(syntax);
        RagAnswerDraft mapped = draftMapper.map(schema);
        RagAnswerDraft business = businessValidator.validate(mapped, input);
        return citationGuard.validate(business, input);
    }

    private JsonNode validateSyntax(String raw) {
        if (raw == null || raw.isBlank()) throw syntax("EMPTY_MODEL_OUTPUT");
        if (raw.length() > maxRawDraftChars) {
            throw new RagAnswerValidationException(
                    RagAnswerValidationStage.JSON_SYNTAX,
                    List.of(new RagAnswerValidationIssue("MODEL_OUTPUT_TOO_LARGE", "$", false)));
        }
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject()) throw syntax("JSON_ROOT_NOT_OBJECT");
            return root;
        } catch (JsonProcessingException exception) {
            throw syntax("INVALID_JSON_SYNTAX");
        }
    }

    private RagAnswerValidationException syntax(String code) {
        return new RagAnswerValidationException(
                RagAnswerValidationStage.JSON_SYNTAX,
                List.of(new RagAnswerValidationIssue(code, "$", true)));
    }
}
