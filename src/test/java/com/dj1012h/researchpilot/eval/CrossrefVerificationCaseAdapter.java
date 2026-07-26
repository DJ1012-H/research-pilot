package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;

import java.util.ArrayList;
import java.util.List;

/** Test-only JSONL adapter. It fails explicitly instead of repairing reviewed source data. */
final class CrossrefVerificationCaseAdapter {

    private CrossrefVerificationCaseAdapter() {
    }

    static CandidatePaper candidate(JsonNode caseNode) {
        JsonNode candidate = requiredObject(caseNode, "input", "candidate");
        List<CandidatePaper.Author> authors = new ArrayList<>();
        JsonNode authorNodes = requiredArray(candidate, "authors");
        for (JsonNode author : authorNodes) {
            authors.add(new CandidatePaper.Author(
                    null,
                    requiredText(author, "display_name"),
                    nullableText(author, "orcid")
            ));
        }
        return new CandidatePaper(
                nullableText(candidate, "openalex_id"),
                nullableText(candidate, "doi"),
                nullableText(candidate, "title"),
                authors,
                nullableText(candidate, "venue"),
                null,
                nullableInteger(candidate, "publication_year"),
                nullableText(candidate, "work_type"),
                0,
                null,
                null,
                null,
                false,
                CandidatePaper.CandidateSource.OPENALEX
        );
    }

    static CrossrefWorkMetadata reference(JsonNode caseNode) {
        JsonNode reference = requiredObject(caseNode, "input", "reference");
        List<String> authors = new ArrayList<>();
        for (JsonNode author : requiredArray(reference, "authors")) {
            authors.add(requiredText(author));
        }
        return new CrossrefWorkMetadata(
                requiredText(reference, "doi"),
                nullableText(reference, "title"),
                authors,
                nullableInteger(reference, "publication_year"),
                nullableText(reference, "venue"),
                nullableText(reference, "work_type"),
                nullableText(reference, "publisher")
        );
    }

    private static JsonNode requiredObject(JsonNode root, String first, String second) {
        JsonNode object = root.path(first).path(second);
        if (!object.isObject()) throw new IllegalArgumentException("Expected object at " + first + "." + second);
        return object;
    }

    private static JsonNode requiredArray(JsonNode root, String field) {
        JsonNode array = root.path(field);
        if (!array.isArray()) throw new IllegalArgumentException("Expected array at " + field);
        return array;
    }

    private static String requiredText(JsonNode root, String field) {
        return requiredText(root.path(field));
    }

    private static String requiredText(JsonNode value) {
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Expected nonblank text value: " + value);
        }
        return value.asText();
    }

    private static String nullableText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isNull() ? null : requiredText(value);
    }

    private static Integer nullableInteger(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isNull()) return null;
        if (!value.isIntegralNumber()) throw new IllegalArgumentException("Expected integer at " + field);
        return value.intValue();
    }
}
