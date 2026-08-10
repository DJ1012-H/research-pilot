package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dj1012h.researchpilot.literature.model.FieldMatchStatus;
import com.dj1012h.researchpilot.literature.model.VerificationField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CrossrefVerificationDatasetSupport {

    static final Path DATASET = Path.of("eval", "crossref-verification-v1");
    static final List<Path> CASE_FILES = List.of(
            DATASET.resolve("draft/seed-cases.jsonl"),
            DATASET.resolve("generated/doi-normalization-cases.jsonl"),
            DATASET.resolve("generated/title-normalization-cases.jsonl"),
            DATASET.resolve("generated/conflict-cases.jsonl"),
            DATASET.resolve("generated/online-first-and-unicode-hyphen-cases.jsonl")
    );
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CrossrefVerificationDatasetSupport() {
    }

    static Map<String, JsonNode> casesById() throws IOException {
        Map<String, JsonNode> cases = new LinkedHashMap<>();
        for (Path file : CASE_FILES) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonNode caseNode = OBJECT_MAPPER.readTree(line);
                String caseId = caseNode.path("case_id").asText();
                if (caseId.isBlank() || cases.putIfAbsent(caseId, caseNode) != null) {
                    throw new IllegalStateException("Duplicate or blank dataset case_id: " + caseId);
                }
            }
        }
        return Map.copyOf(cases);
    }

    static CalibrationSplit readSplit() throws IOException {
        JsonNode split = OBJECT_MAPPER.readTree(Files.readString(
                DATASET.resolve("manifests/calibration-split-v0.1.json"), StandardCharsets.UTF_8));
        return new CalibrationSplit(
                split.path("version").asText(),
                split.path("dataset_version").asText(),
                stringList(split.path("calibration_case_ids")),
                stringList(split.path("acceptance_case_ids")),
                excludedCases(split.path("excluded_cases")),
                split.path("notes").asText()
        );
    }

    static Map<VerificationField, FieldMatchStatus> expectedFieldStatuses(JsonNode caseNode) {
        JsonNode fieldResults = caseNode.path("expected").path("field_results");
        Map<VerificationField, FieldMatchStatus> expected = new LinkedHashMap<>();
        expected.put(VerificationField.DOI, expectedStatus(fieldResults.path("doi")));
        expected.put(VerificationField.TITLE, expectedStatus(fieldResults.path("title")));
        expected.put(VerificationField.FIRST_AUTHOR, expectedStatus(fieldResults.path("first_author")));
        expected.put(VerificationField.YEAR, expectedStatus(fieldResults.path("publication_year")));
        expected.put(VerificationField.VENUE, expectedStatus(fieldResults.path("venue")));
        return Map.copyOf(expected);
    }

    private static FieldMatchStatus expectedStatus(JsonNode fieldResult) {
        return switch (fieldResult.path("status").asText()) {
            case "MATCHED" -> FieldMatchStatus.MATCH;
            case "EXPLAINABLE_DIFFERENCE" -> FieldMatchStatus.EXPLAINABLE_DIFFERENCE;
            case "MISMATCHED" -> FieldMatchStatus.MISMATCH;
            default -> throw new IllegalArgumentException("Reviewed field result has no comparable status: " + fieldResult);
        };
    }

    private static List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) values.add(value.asText());
        return List.copyOf(values);
    }

    private static List<ExcludedCase> excludedCases(JsonNode array) {
        List<ExcludedCase> excluded = new ArrayList<>();
        for (JsonNode value : array) {
            excluded.add(new ExcludedCase(value.path("case_id").asText(), value.path("reason").asText()));
        }
        return List.copyOf(excluded);
    }

    record CalibrationSplit(
            String version,
            String datasetVersion,
            List<String> calibrationCaseIds,
            List<String> acceptanceCaseIds,
            List<ExcludedCase> excludedCases,
            String notes
    ) {
    }

    record ExcludedCase(String caseId, String reason) {
    }
}
