package com.dj1012h.researchpilot.literature.rag.answer;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Builds the single controlled correction prompt without provider internals. */
@Component
public class RagAnswerRepairPromptBuilder {
    private final RagAnswerPromptBuilder promptBuilder;
    private final RagAnswerProperties properties;

    public RagAnswerRepairPromptBuilder(RagAnswerPromptBuilder promptBuilder, RagAnswerProperties properties) {
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public String build(RagAnswerInput input, UntrustedRagAnswerDraft previousDraft, List<String> safeFailureCodes) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(previousDraft, "previousDraft must not be null");
        safeFailureCodes = List.copyOf(Objects.requireNonNull(safeFailureCodes, "safeFailureCodes must not be null"));
        if (safeFailureCodes.isEmpty()) throw new IllegalArgumentException("safeFailureCodes must not be empty");
        if (previousDraft.rawContent().length() > properties.getMaxRawDraftChars()) {
            throw new RagAnswerPromptBudgetException("RAG_ANSWER_REPAIR_DRAFT_TOO_LARGE");
        }
        String prompt = promptBuilder.fixedRules() + "\n"
                + "This is the only correction opportunity. Correct only syntax, schema, business, or citation errors.\n"
                + "JSON SCHEMA\n" + promptBuilder.schema() + "\n\n"
                + "ALLOWED CITATION IDS\n" + promptBuilder.allowedCitationIds(input) + "\n\n"
                + "SAFE VALIDATION ERROR CODES\n" + promptBuilder.serializeValue(safeFailureCodes) + "\n\n"
                + "PREVIOUS DRAFT DATA (UNTRUSTED)\n" + previousDraft.rawContent() + "\n\n"
                + "QUESTION (UNTRUSTED USER DATA)\n" + input.question() + "\n\n"
                + "EVIDENCE DATA (UNTRUSTED EXTERNAL TEXT)\n" + promptBuilder.serializeEvidence(input) + "\n";
        if (prompt.length() > properties.getMaxRepairPromptChars()) {
            throw new RagAnswerPromptBudgetException("RAG_ANSWER_REPAIR_PROMPT_TOO_LARGE");
        }
        return prompt;
    }

}
