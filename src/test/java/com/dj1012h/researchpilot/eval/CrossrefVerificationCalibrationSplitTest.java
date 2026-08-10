package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefVerificationCalibrationSplitTest {

    @Test
    void shouldValidateTheFrozenSplitSchemaAndCoverage() throws Exception {
        Path splitPath = CrossrefVerificationDatasetSupport.DATASET.resolve("manifests/calibration-split-v0.1.json");
        Path schemaPath = CrossrefVerificationDatasetSupport.DATASET.resolve("schema/calibration-split.schema.json");
        JsonNode splitNode = CrossrefVerificationDatasetSupport.OBJECT_MAPPER.readTree(Files.readString(splitPath));
        Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SchemaLocation.of(schemaPath.toUri().toString()), Files.newInputStream(schemaPath), InputFormat.JSON);
        assertThat(schema.validate(splitNode)).isEmpty();

        CrossrefVerificationDatasetSupport.CalibrationSplit split = CrossrefVerificationDatasetSupport.readSplit();
        Map<String, JsonNode> cases = CrossrefVerificationDatasetSupport.casesById();
        List<String> excludedIds = split.excludedCases().stream()
                .map(CrossrefVerificationDatasetSupport.ExcludedCase::caseId)
                .toList();
        List<String> allSplitIds = new ArrayList<>();
        allSplitIds.addAll(split.calibrationCaseIds());
        allSplitIds.addAll(split.acceptanceCaseIds());
        allSplitIds.addAll(excludedIds);

        assertThat(split.version()).isNotBlank();
        assertThat(split.datasetVersion()).isEqualTo("crossref-verification-v1");
        assertThat(allSplitIds).doesNotHaveDuplicates();
        assertThat(allSplitIds).containsExactlyInAnyOrderElementsOf(cases.keySet());
        assertThat(split.calibrationCaseIds()).isSorted();
        assertThat(split.acceptanceCaseIds()).isSorted();
        assertThat(excludedIds).isSorted();

        for (String caseId : split.calibrationCaseIds()) {
            assertReviewed(cases.get(caseId), caseId);
        }
        for (String caseId : split.acceptanceCaseIds()) {
            assertReviewed(cases.get(caseId), caseId);
        }
        for (CrossrefVerificationDatasetSupport.ExcludedCase excluded : split.excludedCases()) {
            assertThat(excluded.reason()).isNotBlank();
            assertThat(cases).containsKey(excluded.caseId());
        }
    }

    @Test
    void shouldReserveTheRequiredReviewedCaseTypesForAcceptance() throws Exception {
        CrossrefVerificationDatasetSupport.CalibrationSplit split = CrossrefVerificationDatasetSupport.readSplit();
        Map<String, JsonNode> cases = CrossrefVerificationDatasetSupport.casesById();

        assertThat(split.calibrationCaseIds()).hasSize(10);
        assertThat(split.acceptanceCaseIds()).containsExactly(
                "crv1-case-0002",
                "crv1-case-0010",
                "crv1-case-0011",
                "crv1-case-0013"
        );
        assertThat(split.acceptanceCaseIds()).allSatisfy(caseId -> assertReviewed(cases.get(caseId), caseId));
        assertThat(cases.get("crv1-case-0002").path("lineage").path("mutation_id").asText())
                .isEqualTo("DOI_URL_PREFIX");
        assertThat(cases.get("crv1-case-0010").path("lineage").path("mutation_id").asText())
                .isEqualTo("FIRST_AUTHOR_REPLACEMENT");
        assertThat(cases.get("crv1-case-0011").path("lineage").path("mutation_id").asText())
                .isEqualTo("DISTINCT_TITLE_REPLACEMENT");
        assertThat(cases.get("crv1-case-0013").path("lineage").path("mutation_id").asText())
                .isEqualTo("ONLINE_FIRST_YEAR");
    }

    private static void assertReviewed(JsonNode caseNode, String caseId) {
        assertThat(caseNode).as("split case must resolve: %s", caseId).isNotNull();
        assertThat(caseNode.path("expected").path("review_state").asText())
                .as("split case must be reviewed: %s", caseId)
                .isEqualTo("REVIEWED");
        assertThat(caseNode.path("expected").path("rationale").asText())
                .as("reviewed split case must include rationale: %s", caseId)
                .isNotBlank();
    }
}
