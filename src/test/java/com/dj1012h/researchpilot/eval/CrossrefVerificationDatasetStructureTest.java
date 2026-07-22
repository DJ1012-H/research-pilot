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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefVerificationDatasetStructureTest {

    private static final Path DATASET = Path.of("eval", "crossref-verification-v1");
    private static final String PROVENANCE_SCHEMA_ID =
            "https://researchpilot.local/crossref-verification-v1/source-provenance.schema.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldKeepEverySeedCaseValidAndTraceable() throws Exception {
        Schema caseSchema = schema("verification-case.schema.json");
        List<String> lines = Files.readAllLines(DATASET.resolve("draft/seed-cases.jsonl"), StandardCharsets.UTF_8);
        Set<String> caseIds = new HashSet<>();

        for (String line : lines) {
            if (line.isBlank()) continue;
            JsonNode caseNode = OBJECT_MAPPER.readTree(line);
            assertThat(caseSchema.validate(caseNode)).isEmpty();
            assertThat(caseNode.path("task_type").asText()).isEqualTo("BIBLIOGRAPHIC_VERIFICATION");
            assertThat(caseIds.add(caseNode.path("case_id").asText())).isTrue();
            assertThat(caseNode.has("provenance")).isTrue();
            assertThat(caseNode.has("kind")).isFalse();
        }

        assertThat(Files.exists(DATASET.resolve("draft/review-queue.jsonl"))).isFalse();
    }

    @Test
    void shouldEnforceReviewAndMutationConditions() throws Exception {
        Schema caseSchema = schema("verification-case.schema.json");
        ObjectNode needsReview = exampleCase();
        assertThat(caseSchema.validate(needsReview)).isEmpty();

        ObjectNode reviewedWithoutAudit = needsReview.deepCopy();
        reviewedWithoutAudit.with("expected").put("review_state", "REVIEWED");
        assertThat(caseSchema.validate(reviewedWithoutAudit)).isNotEmpty();

        ObjectNode mutationWithoutLineage = needsReview.deepCopy();
        mutationWithoutLineage.with("lineage").put("is_mutation", true);
        assertThat(caseSchema.validate(mutationWithoutLineage)).isNotEmpty();

        ObjectNode baseWithMutationValues = needsReview.deepCopy();
        baseWithMutationValues.with("lineage").put("parent_case_id", "crv1-draft-0001");
        assertThat(caseSchema.validate(baseWithMutationValues)).isNotEmpty();
    }

    @Test
    void shouldValidateExistingFixtureProvenanceAndHash() throws Exception {
        Schema provenanceSchema = schema("source-provenance.schema.json");
        JsonNode manifest = OBJECT_MAPPER.readTree(Files.readString(DATASET.resolve("manifests/source-provenance.json")));
        assertThat(provenanceSchema.validate(manifest)).isEmpty();

        for (JsonNode source : manifest.path("sources")) {
            JsonNode provenance = source.path("provenance");
            if (!"EXISTING_FIXTURE".equals(provenance.path("origin_type").asText())) continue;
            String sourcePath = provenance.path("source_path").asText();
            assertThat(Path.of(sourcePath).isAbsolute()).isFalse();
            assertThat(sha256(Path.of(sourcePath))).isEqualToIgnoringCase(provenance.path("source_sha256").asText());
        }
    }

    private Schema schema(String fileName) throws IOException {
        Path schemaDirectory = DATASET.resolve("schema").toAbsolutePath();
        Path schemaPath = schemaDirectory.resolve(fileName);
        String provenanceSchema = Files.readString(schemaDirectory.resolve("source-provenance.schema.json"));
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(schemaId ->
                        PROVENANCE_SCHEMA_ID.equals(schemaId) ? provenanceSchema : null)
        );
        return registry.getSchema(SchemaLocation.of(schemaPath.toUri().toString()), Files.newInputStream(schemaPath), InputFormat.JSON);
    }

    private ObjectNode exampleCase() throws IOException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("schema_version", "crossref-verification-v1");
        root.put("case_id", "crv1-draft-0001");
        root.put("task_type", "BIBLIOGRAPHIC_VERIFICATION");
        root.set("input", OBJECT_MAPPER.readTree("""
                        {"candidate":{"openalex_id":null,"doi":"10.1000/example","title":"A title","authors":[{"display_name":"Ada Lovelace","orcid":null}],"publication_year":2024,"venue":"Journal","work_type":"article"},"reference":{"source":"CROSSREF","doi":"10.1000/example","title":"A title","authors":["Ada Lovelace"],"publication_year":2024,"venue":"Journal","work_type":"article","publisher":"Publisher"}}
                        """));
        root.set("expected", OBJECT_MAPPER.readTree("""
                        {"review_state":"NEEDS_REVIEW","verification_status":null,"formal_result_eligible":null,"evidence_score_range":{"min":0,"max":1},"field_results":{"doi":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"title":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"first_author":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"publication_year":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"venue":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null}},"rationale":null}
                        """));
        root.set("provenance", OBJECT_MAPPER.readTree("""
                        {"origin_type":"EXISTING_TEST","source_path":"src/test/java/com/dj1012h/researchpilot/integration/crossref/CrossrefSearchAdapterTest.java","origin_test":"com.dj1012h.researchpilot.integration.crossref.CrossrefSearchAdapterTest#shouldMapFoundMetadataWithOnlineDatePriorityAndMissingOptionalFields","source_url":null,"retrieved_at":null,"source_sha256":null,"review":null}
                        """));
        root.set("lineage", OBJECT_MAPPER.readTree("""
                        {"is_mutation":false,"parent_case_id":null,"mutation_id":null,"mutation_version":null}
                        """));
        root.set("tags", OBJECT_MAPPER.createArrayNode().add("schema-test"));
        root.putNull("notes");
        return root;
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }
}
