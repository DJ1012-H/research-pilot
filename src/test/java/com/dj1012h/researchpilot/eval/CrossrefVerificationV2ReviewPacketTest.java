package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefVerificationV2ReviewPacketTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path DATASET = Path.of("eval", "crossref-verification-v2");
    private static final Path REVIEW_ROOT = DATASET.resolve("review/intake-v0.1");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRecordReviewAuthorizationWithoutGeneratingGroundTruth() throws Exception {
        JsonNode session = OBJECT_MAPPER.readTree(Files.readString(REVIEW_ROOT.resolve("review-session-v0.1.json")));
        assertThat(schema("review-session.schema.json").validate(session)).isEmpty();
        assertThat(session.path("state").asText()).isEqualTo("AWAITING_CASE_DECISIONS");
        assertThat(session.path("authorization").path("approval_phrase").asText())
                .isEqualTo("APPROVE review intake-v0.1");
        assertThat(session.path("reviewer_assignment").isNull()).isTrue();
        assertThat(session.path("outputs").path("decision_count").asInt()).isZero();
        assertThat(session.path("constraints").path("ground_truth_generated").asBoolean()).isFalse();
        assertThat(session.path("constraints").path("promotion_allowed").asBoolean()).isFalse();

        assertFileHash(session.path("input").path("manifest_path"), session.path("input").path("manifest_sha256"));
        assertFileHash(session.path("input").path("queue_path"), session.path("input").path("queue_sha256"));
        assertFileHash(session.path("outputs").path("review_packet_path"),
                session.path("outputs").path("review_packet_sha256"));
        assertFileHash(session.path("outputs").path("review_guide_path"),
                session.path("outputs").path("review_guide_sha256"));

        Path packetPath = repositoryPath(session.path("outputs").path("review_packet_path").asText());
        List<JsonNode> packetCases = Files.readAllLines(packetPath).stream()
                .filter(line -> !line.isBlank())
                .map(CrossrefVerificationV2ReviewPacketTest::readJson)
                .toList();
        assertThat(packetCases).hasSize(20);

        Path queuePath = repositoryPath(session.path("input").path("queue_path").asText());
        Set<String> queueIds = Files.readAllLines(queuePath).stream()
                .filter(line -> !line.isBlank())
                .map(CrossrefVerificationV2ReviewPacketTest::readJson)
                .map(node -> node.path("case_id").asText())
                .collect(Collectors.toSet());
        Schema packetSchema = schema("review-packet-case.schema.json");
        Set<String> packetIds = new HashSet<>();

        JsonNode manifest = OBJECT_MAPPER.readTree(Files.readString(DATASET.resolve("manifests/intake-batch-v0.1.json")));
        Map<String, JsonNode> manifestCases = manifest.path("cases").valueStream()
                .collect(Collectors.toMap(node -> node.path("case_id").asText(), Function.identity()));
        for (JsonNode packetCase : packetCases) {
            assertThat(packetSchema.validate(packetCase)).isEmpty();
            assertThat(packetCase.path("review_state").asText()).isEqualTo("AWAITING_HUMAN_DECISION");
            assertThat(packetIds.add(packetCase.path("case_id").asText())).isTrue();
            assertEmptyDecision(packetCase.path("decision"));
            assertObservationsMatchSnapshots(packetCase, manifestCases.get(packetCase.path("case_id").asText()));
        }
        assertThat(packetIds).containsExactlyInAnyOrderElementsOf(queueIds);
        assertThat(Files.notExists(DATASET.resolve("reviewed"))).isTrue();
        assertThat(Files.notExists(DATASET.resolve("formal"))).isTrue();
    }

    private static void assertEmptyDecision(JsonNode decision) {
        assertThat(decision.path("reviewer").isNull()).isTrue();
        assertThat(decision.path("reviewed_at").isNull()).isTrue();
        assertThat(decision.path("review_version").isNull()).isTrue();
        decision.path("field_oracles").valueStream().forEach(value -> assertThat(value.isNull()).isTrue());
        assertThat(decision.path("policy_status").isNull()).isTrue();
        assertThat(decision.path("formal_admission").isNull()).isTrue();
        assertThat(decision.path("rationale").isNull()).isTrue();
    }

    private static void assertObservationsMatchSnapshots(JsonNode packetCase, JsonNode manifestCase) throws Exception {
        assertThat(manifestCase).isNotNull();
        JsonNode candidateSource = manifestCase.path("candidate_source");
        JsonNode referenceSource = manifestCase.path("reference_source");
        Path candidatePath = repositoryPath(candidateSource.path("snapshot_path").asText());
        Path referencePath = repositoryPath(referenceSource.path("snapshot_path").asText());
        assertThat(sha256(candidatePath)).isEqualTo(candidateSource.path("sha256").asText());
        assertThat(sha256(referencePath)).isEqualTo(referenceSource.path("sha256").asText());

        JsonNode candidate = OBJECT_MAPPER.readTree(Files.readString(candidatePath));
        JsonNode reference = OBJECT_MAPPER.readTree(Files.readString(referencePath)).path("message");
        JsonNode candidateObservation = packetCase.path("observations").path("candidate");
        JsonNode referenceObservation = packetCase.path("observations").path("reference");
        assertThat(candidateObservation.path("doi").asText()).isEqualTo(candidate.path("doi").asText());
        assertThat(candidateObservation.path("title").asText()).isEqualTo(candidate.path("title").asText());
        assertThat(candidateObservation.path("publication_year").asInt()).isEqualTo(candidate.path("publication_year").asInt());
        assertThat(candidateObservation.path("venue").asText())
                .isEqualTo(candidate.path("primary_location").path("source").path("display_name").asText());
        assertThat(candidateObservation.path("authors")).hasSameSizeAs(candidate.path("authorships"));
        assertThat(referenceObservation.path("doi").asText()).isEqualTo(reference.path("DOI").asText());
        assertThat(referenceObservation.path("title").asText()).isEqualTo(reference.path("title").path(0).asText());
        assertThat(referenceObservation.path("venue").asText())
                .isEqualTo(reference.path("container-title").path(0).asText());
        assertThat(referenceObservation.path("authors")).hasSameSizeAs(reference.path("author"));
    }

    private static void assertFileHash(JsonNode path, JsonNode hash) throws Exception {
        assertThat(sha256(repositoryPath(path.asText()))).isEqualTo(hash.asText());
    }

    private static Path repositoryPath(String path) {
        Path resolved = PROJECT_ROOT.resolve(path).normalize();
        assertThat(resolved).startsWith(PROJECT_ROOT.resolve(DATASET).normalize());
        assertThat(Files.isRegularFile(resolved)).isTrue();
        return resolved;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private static JsonNode readJson(String line) {
        try {
            return OBJECT_MAPPER.readTree(line);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid review packet JSONL", exception);
        }
    }

    private Schema schema(String fileName) throws Exception {
        Path schemaPath = DATASET.resolve("schema").resolve(fileName).toAbsolutePath();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        return registry.getSchema(SchemaLocation.of(schemaPath.toUri().toString()),
                Files.newInputStream(schemaPath), InputFormat.JSON);
    }
}
