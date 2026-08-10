package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.dj1012h.researchpilot.integration.crossref.VerificationThresholdProperties;
import com.dj1012h.researchpilot.literature.application.CandidateNormalizationService;
import com.dj1012h.researchpilot.literature.application.EligiblePaperFilter;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CrossrefPolicyBenchmarkTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private CandidateNormalizationService normalizationService;

    @Autowired
    private PaperVerificationService verificationService;

    @Autowired
    private EligiblePaperFilter eligiblePaperFilter;

    @Autowired
    private DoiNormalizer doiNormalizer;

    @Autowired
    private VerificationThresholdProperties thresholds;

    @Test
    void shouldGenerateAndPreserveTheFailClosedMainPolicyBaseline() throws Exception {
        JsonNode manifest = OBJECT_MAPPER.readTree(Files.readString(CrossrefPolicyBenchmarkRunner.MANIFEST));
        assertThat(schema("policy-benchmark-manifest.schema.json").validate(manifest)).isEmpty();
        assertPinnedProductionSources(manifest);
        assertPinnedThresholds(manifest.path("thresholds"));

        CrossrefPolicyBenchmarkRunner runner = new CrossrefPolicyBenchmarkRunner(
                normalizationService, verificationService, eligiblePaperFilter, doiNormalizer);
        CrossrefPolicyBenchmarkRunner.BenchmarkReport report = runner.evaluate();
        runner.write(report);

        assertThat(schema("policy-benchmark-result.schema.json").validate(runner.toJson(report))).isEmpty();
        assertThat(report.evaluatedCaseCount()).isEqualTo(14);
        assertThat(report.calibrationStatusMatchCount()).isEqualTo(2);
        assertThat(report.acceptanceStatusMatchCount()).isEqualTo(2);
        assertThat(report.overallStatusMatchCount()).isEqualTo(4);
        assertThat(report.formalAdmissionMatchCount()).isEqualTo(4);
        assertThat(report.falseVerifiedCount()).isEqualTo(1);
        assertThat(report.falseFormalAdmissionCount()).isEqualTo(1);
        assertThat(report.falseFormalExclusionCount()).isEqualTo(9);
        assertThat(report.exceptionCount()).isZero();
        assertThat(report.acceptancePassed()).isFalse();
        assertThat(report.failureReasons()).containsExactly(
                "OVERALL_STATUS_MATCH_COUNT_4_REQUIRED_14",
                "ACCEPTANCE_STATUS_MATCH_COUNT_2_REQUIRED_4",
                "FALSE_VERIFIED_COUNT_1_MAX_0",
                "FALSE_FORMAL_ADMISSION_COUNT_1_MAX_0",
                "FALSE_FORMAL_EXCLUSION_COUNT_9_MAX_0"
        );
        assertThat(report.caseResults()).filteredOn(result -> result.caseId().compareTo("crv1-case-0010") < 0)
                .allSatisfy(result -> {
                    assertThat(result.expectedStatus()).isEqualTo("VERIFIED");
                    assertThat(result.actualStatus()).isEqualTo("CONFLICTED");
                    assertThat(result.falseFormalExclusion()).isTrue();
                    assertThat(result.policyReasons()).containsExactly("HARD_FIELD_CONFLICT_AUTHORS");
                });
        assertThat(report.caseResults()).filteredOn(result -> result.caseId().equals("crv1-case-0013"))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.expectedStatus()).isEqualTo("PARTIALLY_VERIFIED");
                    assertThat(result.actualStatus()).isEqualTo("VERIFIED");
                    assertThat(result.falseVerified()).isTrue();
                    assertThat(result.falseFormalAdmission()).isTrue();
                    assertThat(result.policyReasons()).containsExactly("DOI_EXACT_MATCH_NO_HARD_CONFLICT");
                });
        assertThat(Files.readString(CrossrefPolicyBenchmarkRunner.MARKDOWN_OUTPUT))
                .contains("Result: **FAIL**", "crv1-case-0013");
    }

    private void assertPinnedProductionSources(JsonNode manifest) throws Exception {
        for (JsonNode source : manifest.path("production_sources")) {
            Path path = Path.of(source.path("path").asText());
            assertThat(path.isAbsolute()).isFalse();
            assertThat(Files.exists(path)).as("Pinned production source must exist: %s", path).isTrue();
            assertThat(sha256(path)).isEqualToIgnoringCase(source.path("sha256").asText());
        }
    }

    private void assertPinnedThresholds(JsonNode expected) {
        assertThat(thresholds).isEqualTo(new VerificationThresholdProperties(
                expected.path("title_strong_match").asDouble(),
                expected.path("title_possible_match").asDouble(),
                expected.path("author_overlap").asDouble(),
                expected.path("source_match").asDouble(),
                expected.path("publication_year_tolerance").asInt()
        ));
    }

    private Schema schema(String fileName) throws Exception {
        Path schemaPath = CrossrefVerificationDatasetSupport.DATASET.resolve("schema").resolve(fileName).toAbsolutePath();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        return registry.getSchema(SchemaLocation.of(schemaPath.toUri().toString()),
                Files.newInputStream(schemaPath), InputFormat.JSON);
    }

    private String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }
}
