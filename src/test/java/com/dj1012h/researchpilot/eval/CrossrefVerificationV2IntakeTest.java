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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefVerificationV2IntakeTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path DATASET = Path.of("eval", "crossref-verification-v2");
    private static final Path ABSOLUTE_DATASET = PROJECT_ROOT.resolve(DATASET).normalize();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldValidateTheImmutableUnreviewedIntakeBatch() throws Exception {
        Path manifestPath = DATASET.resolve("manifests/intake-batch-v0.1.json");
        JsonNode manifest = OBJECT_MAPPER.readTree(Files.readString(manifestPath));
        assertThat(schema("intake-batch.schema.json").validate(manifest)).isEmpty();

        Path selectionPath = checkedRepositoryPath(manifest.path("selection").path("snapshot_path").asText());
        assertThat(sha256(selectionPath)).isEqualTo(manifest.path("selection").path("sha256").asText());

        Path queuePath = checkedRepositoryPath(manifest.path("queue").path("path").asText());
        assertThat(sha256(queuePath)).isEqualTo(manifest.path("queue").path("sha256").asText());
        List<JsonNode> queuedCases = Files.readAllLines(queuePath).stream()
                .filter(line -> !line.isBlank())
                .map(CrossrefVerificationV2IntakeTest::readJson)
                .toList();
        assertThat(queuedCases).hasSize(20);

        Schema queueSchema = schema("review-queue-case.schema.json");
        Map<String, JsonNode> manifestCases = manifest.path("cases").valueStream()
                .collect(Collectors.toMap(node -> node.path("case_id").asText(), Function.identity()));
        assertThat(manifestCases).hasSize(20);

        Set<String> dois = new HashSet<>();
        Set<String> primarySources = new HashSet<>();
        Map<String, Integer> fieldCounts = new HashMap<>();
        Set<String> selectedOpenAlexIds = new HashSet<>();

        for (JsonNode queuedCase : queuedCases) {
            assertThat(queueSchema.validate(queuedCase)).isEmpty();
            assertThat(queuedCase.path("review_state").asText()).isEqualTo("NEEDS_REVIEW");
            assertThat(queuedCase.path("expected").path("policy_status").isNull()).isTrue();
            assertThat(queuedCase.path("expected").path("formal_admission").isNull()).isTrue();
            queuedCase.path("expected").path("field_oracles").valueStream()
                    .forEach(oracle -> assertThat(oracle.isNull()).isTrue());
            assertThat(queuedCase.path("provenance").path("review").isNull()).isTrue();

            String caseId = queuedCase.path("case_id").asText();
            JsonNode manifestCase = manifestCases.get(caseId);
            assertThat(manifestCase).as("Manifest entry for %s", caseId).isNotNull();

            JsonNode candidateSource = sourceWithRole(queuedCase, "CANDIDATE");
            JsonNode referenceSource = sourceWithRole(queuedCase, "REFERENCE");
            assertSourceMatchesManifest(candidateSource, manifestCase.path("candidate_source"));
            assertSourceMatchesManifest(referenceSource, manifestCase.path("reference_source"));

            JsonNode candidate = verifiedSnapshot(candidateSource);
            JsonNode reference = verifiedSnapshot(referenceSource);
            String candidateDoi = normalizeDoi(candidate.path("doi").asText());
            String referenceDoi = normalizeDoi(reference.path("message").path("DOI").asText());
            assertThat(reference.path("status").asText()).isEqualTo("ok");
            assertThat(candidateDoi).isEqualTo(referenceDoi).isEqualTo(manifestCase.path("doi").asText());
            assertThat(dois.add(candidateDoi)).as("Unique DOI for %s", caseId).isTrue();
            assertThat(candidate.path("type").asText()).isEqualTo("article");
            assertThat(candidate.path("is_retracted").asBoolean()).isFalse();
            assertThat(candidate.path("authorships").isArray()).isTrue();
            assertThat(candidate.path("authorships")).isNotEmpty();

            String primarySourceId = candidate.path("primary_location").path("source").path("id").asText();
            String fieldId = candidate.path("primary_topic").path("field").path("id").asText();
            assertThat(primarySources.add(primarySourceId)).as("Unique primary source for %s", caseId).isTrue();
            fieldCounts.merge(fieldId, 1, Integer::sum);
            selectedOpenAlexIds.add(candidate.path("id").asText());
        }

        assertThat(fieldCounts.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(3));
        JsonNode selection = OBJECT_MAPPER.readTree(Files.readString(selectionPath));
        Set<String> sampledOpenAlexIds = selection.path("results").valueStream()
                .map(node -> node.path("id").asText())
                .collect(Collectors.toSet());
        assertThat(sampledOpenAlexIds).containsAll(selectedOpenAlexIds);
        assertThat(Files.notExists(DATASET.resolve("reviewed"))).isTrue();
        assertThat(Files.notExists(DATASET.resolve("formal"))).isTrue();
    }

    private static JsonNode sourceWithRole(JsonNode queuedCase, String role) {
        List<JsonNode> matches = queuedCase.path("provenance").path("sources").valueStream()
                .filter(source -> role.equals(source.path("role").asText()))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private static void assertSourceMatchesManifest(JsonNode queuedSource, JsonNode manifestSource) {
        assertThat(queuedSource.path("source_id").asText()).isEqualTo(manifestSource.path("source_id").asText());
        assertThat(queuedSource.path("snapshot_path").asText()).isEqualTo(manifestSource.path("snapshot_path").asText());
        assertThat(queuedSource.path("source_url").asText()).isEqualTo(manifestSource.path("source_url").asText());
        assertThat(queuedSource.path("retrieved_at").asText()).isEqualTo(manifestSource.path("retrieved_at").asText());
        assertThat(queuedSource.path("sha256").asText()).isEqualTo(manifestSource.path("sha256").asText());
    }

    private static JsonNode verifiedSnapshot(JsonNode source) throws Exception {
        Path snapshot = checkedRepositoryPath(source.path("snapshot_path").asText());
        assertThat(sha256(snapshot)).isEqualTo(source.path("sha256").asText());
        return OBJECT_MAPPER.readTree(Files.readString(snapshot));
    }

    private static Path checkedRepositoryPath(String repositoryPath) {
        Path resolved = PROJECT_ROOT.resolve(repositoryPath).normalize();
        assertThat(resolved).startsWith(ABSOLUTE_DATASET);
        assertThat(Files.isRegularFile(resolved)).as("Snapshot exists: %s", repositoryPath).isTrue();
        return resolved;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private static String normalizeDoi(String doi) {
        return doi.trim()
                .replaceFirst("(?i)^https?://(?:dx\\.)?doi\\.org/", "")
                .toLowerCase(Locale.ROOT);
    }

    private static JsonNode readJson(String line) {
        try {
            return OBJECT_MAPPER.readTree(line);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid intake JSONL", exception);
        }
    }

    private Schema schema(String fileName) throws Exception {
        Path schemaPath = DATASET.resolve("schema").resolve(fileName).toAbsolutePath();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        return registry.getSchema(SchemaLocation.of(schemaPath.toUri().toString()),
                Files.newInputStream(schemaPath), InputFormat.JSON);
    }
}
