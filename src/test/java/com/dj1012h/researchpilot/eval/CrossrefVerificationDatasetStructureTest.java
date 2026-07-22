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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossrefVerificationDatasetStructureTest {

    private static final Path DATASET = Path.of("eval", "crossref-verification-v1");
    private static final String OPENALEX_FIXTURE_SOURCE_ID = "openalex-works-response-fixture";
    private static final String PROVENANCE_SCHEMA_ID =
            "https://researchpilot.local/crossref-verification-v1/source-provenance.schema.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldValidateEveryNonBlankSeedCase() throws Exception {
        Schema caseSchema = schema("verification-case.schema.json");
        Set<String> sourceIds = sourceIds();
        List<String> lines = Files.readAllLines(DATASET.resolve("draft/seed-cases.jsonl"), StandardCharsets.UTF_8);
        Set<String> caseIds = new HashSet<>();

        for (String line : lines) {
            if (line.isBlank()) continue;
            JsonNode caseNode = OBJECT_MAPPER.readTree(line);
            validateCase(caseSchema, caseNode, sourceIds);
            assertThat(caseIds.add(caseNode.path("case_id").asText())).isTrue();
            assertThat(caseNode.has("kind")).isFalse();
        }

        assertThat(lines.stream().filter(line -> !line.isBlank())).isNotEmpty();
        assertThat(Files.exists(DATASET.resolve("draft/review-queue.jsonl"))).isFalse();
    }

    @Test
    void shouldRequireResolvableDualSourcesAndStableCaseIds() throws Exception {
        Schema caseSchema = schema("verification-case.schema.json");
        Set<String> sourceIds = sourceIds();
        ObjectNode caseNode = exampleCase();

        validateCase(caseSchema, caseNode, sourceIds);

        ObjectNode missingCandidateSource = caseNode.deepCopy();
        missingCandidateSource.with("provenance").put("candidate_source_id", "");
        assertThat(caseSchema.validate(missingCandidateSource)).isNotEmpty();

        ObjectNode unresolvedReferenceSource = caseNode.deepCopy();
        unresolvedReferenceSource.with("provenance").put("reference_source_id", "crossref-snapshot-identifier");
        assertThatThrownBy(() -> validateCase(caseSchema, unresolvedReferenceSource, sourceIds))
                .isInstanceOf(AssertionError.class);

        ObjectNode legacyId = caseNode.deepCopy();
        legacyId.put("case_id", "crv1-draft-0001");
        assertThat(caseSchema.validate(legacyId)).isNotEmpty();
    }

    @Test
    void shouldEnforceReviewAndEvidenceScoreConditions() throws Exception {
        Schema caseSchema = schema("verification-case.schema.json");
        Set<String> sourceIds = sourceIds();
        ObjectNode needsReview = exampleCase();
        validateCase(caseSchema, needsReview, sourceIds);

        ObjectNode needsReviewWithAudit = needsReview.deepCopy();
        needsReviewWithAudit.with("provenance").set("review", review());
        assertThat(caseSchema.validate(needsReviewWithAudit)).isNotEmpty();

        ObjectNode reviewedWithoutAudit = needsReview.deepCopy();
        reviewedWithoutAudit.with("expected").put("review_state", "REVIEWED");
        reviewedWithoutAudit.with("expected").put("verification_status", "VERIFIED");
        reviewedWithoutAudit.with("expected").put("formal_result_eligible", true);
        reviewedWithoutAudit.with("expected").put("rationale", "Reviewed fixture contract.");
        assertThat(caseSchema.validate(reviewedWithoutAudit)).isNotEmpty();

        ObjectNode reviewed = reviewedWithoutAudit.deepCopy();
        reviewed.with("provenance").set("review", review());
        reviewed.with("expected").set("evidence_score_range", scoreRange(0.0, 1.0));
        validateCase(caseSchema, reviewed, sourceIds);

        ObjectNode belowMinimum = reviewed.deepCopy();
        belowMinimum.with("expected").set("evidence_score_range", scoreRange(-0.01, 0.5));
        assertThat(caseSchema.validate(belowMinimum)).isNotEmpty();

        ObjectNode aboveMaximum = reviewed.deepCopy();
        aboveMaximum.with("expected").set("evidence_score_range", scoreRange(0.5, 1.01));
        assertThat(caseSchema.validate(aboveMaximum)).isNotEmpty();

        ObjectNode reversedRange = reviewed.deepCopy();
        reversedRange.with("expected").set("evidence_score_range", scoreRange(0.8, 0.2));
        assertThatThrownBy(() -> validateCase(caseSchema, reversedRange, sourceIds))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldAcceptOnlyBibliographicVerificationStatusesAndCompleteMutationLineage() throws Exception {
        Schema caseSchema = schema("verification-case.schema.json");
        Set<String> sourceIds = sourceIds();
        for (String status : List.of("VERIFIED", "PARTIALLY_VERIFIED", "CONFLICTED", "REJECTED")) {
            ObjectNode reviewed = reviewedCase(status);
            validateCase(caseSchema, reviewed, sourceIds);
        }

        for (String rejectedStatus : List.of("NOT_FOUND", "SOURCE_UNAVAILABLE")) {
            ObjectNode invalid = reviewedCase(rejectedStatus);
            assertThat(caseSchema.validate(invalid)).isNotEmpty();
        }

        ObjectNode mutationWithoutLineage = exampleCase();
        mutationWithoutLineage.with("lineage").put("is_mutation", true);
        assertThat(caseSchema.validate(mutationWithoutLineage)).isNotEmpty();

        ObjectNode mutation = exampleCase();
        mutation.with("lineage").put("is_mutation", true);
        mutation.with("lineage").put("parent_case_id", "crv1-case-0001");
        mutation.with("lineage").put("mutation_id", "DOI_CASE_VARIANT");
        mutation.with("lineage").put("mutation_version", "1");
        validateCase(caseSchema, mutation, sourceIds);
    }

    @Test
    void shouldValidateFixtureProvenanceAndHash() throws Exception {
        Schema provenanceSchema = schema("source-provenance.schema.json");
        JsonNode manifest = manifest();
        assertThat(provenanceSchema.validate(manifest)).isEmpty();

        for (JsonNode source : manifest.path("sources")) {
            JsonNode provenance = source.path("provenance");
            assertThat(provenance.has("review")).isFalse();
            if (!"EXISTING_FIXTURE".equals(provenance.path("origin_type").asText())) continue;
            String sourcePath = provenance.path("source_path").asText();
            assertThat(Path.of(sourcePath).isAbsolute()).isFalse();
            assertThat(sha256(Path.of(sourcePath))).isEqualToIgnoringCase(provenance.path("source_sha256").asText());
        }
    }

    private void validateCase(Schema caseSchema, JsonNode caseNode, Set<String> sourceIds) {
        assertThat(caseSchema.validate(caseNode)).isEmpty();
        assertThat(caseNode.path("task_type").asText()).isEqualTo("BIBLIOGRAPHIC_VERIFICATION");
        assertThat(sourceIds).contains(caseNode.path("provenance").path("candidate_source_id").asText());
        assertThat(sourceIds).contains(caseNode.path("provenance").path("reference_source_id").asText());
        assertEvidenceScoreRange(caseNode.path("expected").path("evidence_score_range"));
    }

    private void assertEvidenceScoreRange(JsonNode scoreRange) {
        if (scoreRange.isNull()) return;
        assertThat(scoreRange.path("min").decimalValue())
                .isLessThanOrEqualTo(scoreRange.path("max").decimalValue());
    }

    private Set<String> sourceIds() throws IOException {
        Set<String> sourceIds = new HashSet<>();
        for (JsonNode source : manifest().path("sources")) {
            assertThat(sourceIds.add(source.path("source_id").asText())).isTrue();
        }
        return sourceIds;
    }

    private JsonNode manifest() throws IOException {
        return OBJECT_MAPPER.readTree(Files.readString(DATASET.resolve("manifests/source-provenance.json")));
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
        return registry.getSchema(SchemaLocation.of(schemaPath.toUri().toString()),
                Files.newInputStream(schemaPath), InputFormat.JSON);
    }

    private ObjectNode exampleCase() throws IOException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("schema_version", "crossref-verification-v1");
        root.put("case_id", "crv1-case-0001");
        root.put("task_type", "BIBLIOGRAPHIC_VERIFICATION");
        root.set("input", OBJECT_MAPPER.readTree("""
                {"candidate":{"openalex_id":null,"doi":"10.1000/example","title":"A title","authors":[{"display_name":"Ada Lovelace","orcid":null}],"publication_year":2024,"venue":"Journal","work_type":"article"},"reference":{"source":"CROSSREF","doi":"10.1000/example","title":"A title","authors":["Ada Lovelace"],"publication_year":2024,"venue":"Journal","work_type":"article","publisher":"Publisher"}}
                """));
        root.set("expected", OBJECT_MAPPER.readTree("""
                {"review_state":"NEEDS_REVIEW","verification_status":null,"formal_result_eligible":null,"evidence_score_range":null,"field_results":{"doi":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"title":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"first_author":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"publication_year":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null},"venue":{"status":"UNKNOWN","oracle":"FIXTURE","rule_id":null,"reason":null}},"rationale":null}
                """));
        root.set("provenance", OBJECT_MAPPER.readTree("""
                {"candidate_source_id":"openalex-works-response-fixture","reference_source_id":"openalex-works-response-fixture","review":null}
                """));
        root.set("lineage", OBJECT_MAPPER.readTree("""
                {"is_mutation":false,"parent_case_id":null,"mutation_id":null,"mutation_version":null}
                """));
        root.set("tags", OBJECT_MAPPER.createArrayNode().add("schema-test"));
        root.putNull("notes");
        return root;
    }

    private ObjectNode reviewedCase(String status) throws Exception {
        ObjectNode reviewed = exampleCase();
        reviewed.with("expected").put("review_state", "REVIEWED");
        reviewed.with("expected").put("verification_status", status);
        reviewed.with("expected").put("formal_result_eligible", true);
        reviewed.with("expected").put("rationale", "Reviewed schema contract.");
        reviewed.with("expected").set("evidence_score_range", scoreRange(0.0, 1.0));
        reviewed.with("provenance").set("review", review());
        return reviewed;
    }

    private ObjectNode review() {
        ObjectNode review = OBJECT_MAPPER.createObjectNode();
        review.put("reviewer", "schema-test");
        review.put("reviewed_at", "2026-07-22T10:00:00Z");
        review.put("review_version", "1");
        return review;
    }

    private ObjectNode scoreRange(double min, double max) {
        ObjectNode range = OBJECT_MAPPER.createObjectNode();
        range.put("min", min);
        range.put("max", max);
        return range;
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }
}
