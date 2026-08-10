package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefVerificationV2ScaffoldTest {

    private static final Path DATASET = Path.of("eval", "crossref-verification-v2");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldKeepTheUnreviewedV2IntakeFailClosed() throws Exception {
        Path planPath = DATASET.resolve("manifests/coverage-plan-v0.1.json");
        JsonNode plan = OBJECT_MAPPER.readTree(Files.readString(planPath));

        assertThat(schema("coverage-plan.schema.json").validate(plan)).isEmpty();
        assertThat(plan.path("state").asText()).isEqualTo("UNMEASURED");
        assertThat(plan.path("formal_case_count").asInt()).isZero();
        assertThat(plan.path("reviewed_case_count").asInt()).isZero();
        assertThat(plan.path("unreviewed_intake_pair_count").asInt()).isEqualTo(20);
        assertThat(DATASET.resolve(plan.path("intake_batch_manifest").asText())).isRegularFile();
        assertThat(plan.path("review_session_state").asText()).isEqualTo("AWAITING_CASE_DECISIONS");
        assertThat(plan.path("human_case_decision_count").asInt()).isZero();
        assertThat(plan.path("independent_holdout_frozen").asBoolean()).isFalse();
        assertThat(plan.path("required_field_oracles")).extracting(JsonNode::asText)
                .contains("AUTHORS", "WORK_TYPE", "FORMAL_ADMISSION");
        assertThat(plan.path("coverage_requirements")).allSatisfy(requirement ->
                assertThat(requirement.path("reviewed_count").asInt()).isZero());
        assertThat(plan.path("separate_dataset_requirements")).extracting(JsonNode::asText)
                .containsExactly("crossref-lookup-protocol-v1", "literature-orchestration-v1");

        Schema reviewQueueSchema = schema("review-queue-case.schema.json");
        List<Path> queuedCases;
        try (var files = Files.list(DATASET.resolve("draft"))) {
            queuedCases = files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList();
        }
        for (Path queue : queuedCases) {
            for (String line : Files.readAllLines(queue)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode queuedCase = OBJECT_MAPPER.readTree(line);
                assertThat(reviewQueueSchema.validate(queuedCase)).isEmpty();
                assertThat(queuedCase.path("review_state").asText()).isEqualTo("NEEDS_REVIEW");
                assertThat(queuedCase.path("expected").path("policy_status").isNull()).isTrue();
                assertThat(queuedCase.path("expected").path("formal_admission").isNull()).isTrue();
                assertThat(queuedCase.path("provenance").path("review").isNull()).isTrue();
            }
        }

        ObjectNode intake = (ObjectNode) OBJECT_MAPPER.readTree("""
                {
                  "schema_version":"crossref-verification-v2-review-queue",
                  "case_id":"crv2-case-0001",
                  "review_state":"NEEDS_REVIEW",
                  "input":{"candidate_source_id":"candidate-source","reference_source_ids":["reference-source"]},
                  "expected":{"policy_status":null,"formal_admission":null,"field_oracles":{"doi":null,"title":null,"first_author":null,"authors":null,"year":null,"venue":null,"work_type":null}},
                  "provenance":{"sources":[{"source_id":"candidate-source","role":"CANDIDATE","snapshot_path":"eval/crossref-verification-v2/fixtures/candidate.json","source_url":"https://example.invalid/candidate","retrieved_at":"2026-08-10T00:00:00Z","sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"},{"source_id":"reference-source","role":"REFERENCE","snapshot_path":"eval/crossref-verification-v2/fixtures/reference.json","source_url":"https://example.invalid/reference","retrieved_at":"2026-08-10T00:00:00Z","sha256":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"}],"review":null},
                  "notes":null
                }
                """);
        assertThat(reviewQueueSchema.validate(intake)).isEmpty();

        ObjectNode attemptedPromotion = intake.deepCopy();
        attemptedPromotion.put("review_state", "REVIEWED");
        attemptedPromotion.with("expected").put("policy_status", "VERIFIED");
        assertThat(reviewQueueSchema.validate(attemptedPromotion))
                .as("Review-queue schema must reject unapproved promotion and generated labels")
                .isNotEmpty();
    }

    private Schema schema(String fileName) throws Exception {
        Path schemaPath = DATASET.resolve("schema").resolve(fileName).toAbsolutePath();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        return registry.getSchema(SchemaLocation.of(schemaPath.toUri().toString()),
                Files.newInputStream(schemaPath), InputFormat.JSON);
    }
}
