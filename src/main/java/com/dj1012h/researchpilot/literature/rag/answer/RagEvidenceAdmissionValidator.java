package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Syntax, schema, strict DTO, state consistency, and current-request ownership validation. */
@Component
public class RagEvidenceAdmissionValidator {

    private static final int MAX_RAW_DRAFT_CHARS = 2_000;
    private final StructuredOutputMapper mapper;
    private final Schema schema;

    public RagEvidenceAdmissionValidator(StructuredOutputMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        try {
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(
                    new ClassPathResource("schema/" + RagEvidenceAdmissionPromptBuilder.PROMPT_VERSION
                            + ".schema.json").getInputStream(),
                    InputFormat.JSON);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("unable to load RAG evidence admission schema", exception);
        }
    }

    public RagEvidenceAdmissionDecision validate(String raw, RagAnswerInput input) {
        Objects.requireNonNull(input, "input must not be null");
        JsonNode root = syntax(raw);
        if (!schema.validate(root).isEmpty()) {
            throw invalid("RAG_ADMISSION_SCHEMA_INVALID");
        }
        RagEvidenceAdmissionDraft draft;
        try {
            draft = mapper.treeToValue(root, RagEvidenceAdmissionDraft.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalid("RAG_ADMISSION_DTO_INVALID");
        }
        if (draft.reason().isBlank()) {
            throw invalid("RAG_ADMISSION_REASON_EMPTY");
        }
        if (draft.relevant() != !draft.admittedEvidenceIds().isEmpty()) {
            throw invalid("RAG_ADMISSION_STATE_INCONSISTENT");
        }
        Set<String> allowed = input.evidence().stream()
                .map(RagAnswerEvidence::citationId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!allowed.containsAll(draft.admittedEvidenceIds())) {
            throw invalid("RAG_ADMISSION_EVIDENCE_OUTSIDE_REQUEST");
        }
        return new RagEvidenceAdmissionDecision(
                draft.relevant(), draft.admittedEvidenceIds(), draft.reason());
    }

    private JsonNode syntax(String raw) {
        if (raw == null || raw.isBlank()) throw invalid("RAG_ADMISSION_OUTPUT_EMPTY");
        if (raw.length() > MAX_RAW_DRAFT_CHARS) throw invalid("RAG_ADMISSION_OUTPUT_TOO_LARGE");
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject()) throw invalid("RAG_ADMISSION_ROOT_NOT_OBJECT");
            return root;
        } catch (JsonProcessingException exception) {
            throw invalid("RAG_ADMISSION_JSON_INVALID");
        }
    }

    private RagEvidenceAdmissionValidationException invalid(String code) {
        return new RagEvidenceAdmissionValidationException(code);
    }
}
