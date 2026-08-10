package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.application.CandidateNormalizationService;
import com.dj1012h.researchpilot.literature.application.CrossrefLookupSummary;
import com.dj1012h.researchpilot.literature.application.EligiblePaperFilter;
import com.dj1012h.researchpilot.literature.application.PaperVerificationService;
import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidateLookupResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline policy benchmark over reviewed v1 cases. It performs no provider or model calls. */
final class CrossrefPolicyBenchmarkRunner {

    static final Path MANIFEST = CrossrefVerificationDatasetSupport.DATASET
            .resolve("manifests/policy-benchmark-v0.2.json");
    static final Path JSON_OUTPUT = Path.of("target", "evaluation", "crossref-verification-v1",
            "policy-benchmark-v0.2.json");
    static final Path MARKDOWN_OUTPUT = Path.of("target", "evaluation", "crossref-verification-v1",
            "policy-benchmark-v0.2.md");

    private final CandidateNormalizationService normalizationService;
    private final PaperVerificationService verificationService;
    private final EligiblePaperFilter eligiblePaperFilter;
    private final DoiNormalizer doiNormalizer;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    CrossrefPolicyBenchmarkRunner(
            CandidateNormalizationService normalizationService,
            PaperVerificationService verificationService,
            EligiblePaperFilter eligiblePaperFilter,
            DoiNormalizer doiNormalizer
    ) {
        this.normalizationService = normalizationService;
        this.verificationService = verificationService;
        this.eligiblePaperFilter = eligiblePaperFilter;
        this.doiNormalizer = doiNormalizer;
    }

    BenchmarkReport evaluate() throws IOException {
        JsonNode manifest = objectMapper.readTree(Files.readString(MANIFEST, StandardCharsets.UTF_8));
        JsonNode criteria = manifest.path("acceptance_criteria");
        CrossrefVerificationDatasetSupport.CalibrationSplit split = CrossrefVerificationDatasetSupport.readSplit();
        Set<String> calibrationIds = new HashSet<>(split.calibrationCaseIds());
        Set<String> acceptanceIds = new HashSet<>(split.acceptanceCaseIds());
        List<Map.Entry<String, JsonNode>> cases = CrossrefVerificationDatasetSupport.casesById().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        List<CaseResult> caseResults = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : cases) {
            String caseId = entry.getKey();
            String splitName = splitName(caseId, calibrationIds, acceptanceIds);
            caseResults.add(evaluateCase(caseId, splitName, entry.getValue()));
        }

        int calibrationCount = count(caseResults, "CALIBRATION", null);
        int acceptanceCount = count(caseResults, "ACCEPTANCE", null);
        int overallStatusMatches = (int) caseResults.stream().filter(CaseResult::statusMatch).count();
        int calibrationStatusMatches = count(caseResults, "CALIBRATION", CaseResult::statusMatch);
        int acceptanceStatusMatches = count(caseResults, "ACCEPTANCE", CaseResult::statusMatch);
        int formalAdmissionMatches = (int) caseResults.stream().filter(CaseResult::admissionMatch).count();
        int falseVerifiedCount = (int) caseResults.stream().filter(CaseResult::falseVerified).count();
        int falseFormalAdmissionCount = (int) caseResults.stream().filter(CaseResult::falseFormalAdmission).count();
        int falseFormalExclusionCount = (int) caseResults.stream().filter(CaseResult::falseFormalExclusion).count();
        int exceptionCount = (int) caseResults.stream().filter(result -> result.errorCode() != null).count();
        List<String> failures = new ArrayList<>();

        requireEqual(failures, "REVIEWED_CASE_COUNT", cases.size(),
                criteria.path("required_reviewed_case_count").asInt());
        requireEqual(failures, "OVERALL_STATUS_MATCH_COUNT", overallStatusMatches,
                criteria.path("required_overall_status_match_count").asInt());
        requireEqual(failures, "ACCEPTANCE_STATUS_MATCH_COUNT", acceptanceStatusMatches,
                criteria.path("required_acceptance_status_match_count").asInt());
        requireAtMost(failures, "FALSE_VERIFIED_COUNT", falseVerifiedCount,
                criteria.path("max_false_verified_count").asInt());
        requireAtMost(failures, "FALSE_FORMAL_ADMISSION_COUNT", falseFormalAdmissionCount,
                criteria.path("max_false_formal_admission_count").asInt());
        requireAtMost(failures, "FALSE_FORMAL_EXCLUSION_COUNT", falseFormalExclusionCount,
                criteria.path("max_false_formal_exclusion_count").asInt());
        requireAtMost(failures, "EXCEPTION_COUNT", exceptionCount,
                criteria.path("max_exception_count").asInt());

        return new BenchmarkReport(
                "crossref-policy-benchmark-result-v0.1",
                manifest.path("version").asText(),
                manifest.path("dataset_version").asText(),
                split.version(),
                manifest.path("production_baseline_commit").asText(),
                VerificationPolicy.class.getName(),
                manifest.path("formal_admission_oracle").asText(),
                manifest.path("legacy_formal_result_eligible_handling").asText(),
                cases.size(),
                calibrationCount,
                acceptanceCount,
                overallStatusMatches,
                calibrationStatusMatches,
                acceptanceStatusMatches,
                formalAdmissionMatches,
                falseVerifiedCount,
                falseFormalAdmissionCount,
                falseFormalExclusionCount,
                exceptionCount,
                failures.isEmpty(),
                List.copyOf(failures),
                List.copyOf(caseResults)
        );
    }

    void write(BenchmarkReport report) throws IOException {
        Files.createDirectories(JSON_OUTPUT.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(JSON_OUTPUT.toFile(), report);
        Files.writeString(MARKDOWN_OUTPUT, markdown(report), StandardCharsets.UTF_8);
    }

    JsonNode toJson(BenchmarkReport report) {
        return objectMapper.valueToTree(report);
    }

    private CaseResult evaluateCase(String caseId, String split, JsonNode caseNode) {
        String expectedStatus = caseNode.path("expected").path("verification_status").asText();
        boolean legacyFormalResultEligible = caseNode.path("expected").path("formal_result_eligible").asBoolean();
        String reviewedReferenceDoi = caseNode.path("input").path("reference").path("doi").isTextual()
                ? caseNode.path("input").path("reference").path("doi").asText()
                : null;
        boolean expectedFormalAdmission = "VERIFIED".equals(expectedStatus)
                && doiNormalizer.normalize(reviewedReferenceDoi) != null;
        try {
            if (!"REVIEWED".equals(caseNode.path("expected").path("review_state").asText())) {
                throw new IllegalStateException("policy benchmark requires reviewed cases");
            }
            CandidatePaper candidate = CrossrefVerificationCaseAdapter.candidate(caseNode);
            CrossrefWorkMetadata reference = CrossrefVerificationCaseAdapter.reference(caseNode);
            NormalizedCandidate normalized = normalizationService.normalize(candidate, 0);
            CandidateLookupResult.LookupRoute route = normalized.normalizedDoi() == null
                    ? CandidateLookupResult.LookupRoute.BIBLIOGRAPHIC
                    : CandidateLookupResult.LookupRoute.DOI;
            CandidateLookupResult lookup = new CandidateLookupResult(
                    normalized,
                    route,
                    CandidateLookupResult.LookupStatus.FOUND,
                    List.of(reference),
                    "OFFLINE_REVIEWED_FIXTURE"
            );
            CandidateDeduplicationResult deduplication = new CandidateDeduplicationResult(
                    List.of(normalized), List.of(), 1, 1, 0);
            CrossrefLookupSummary summary = new CrossrefLookupSummary(
                    route == CandidateLookupResult.LookupRoute.DOI ? 1 : 0,
                    route == CandidateLookupResult.LookupRoute.BIBLIOGRAPHIC ? 1 : 0,
                    1, 1, 0, 0, 0, true, true,
                    List.of(reference), List.of(), List.of(lookup), deduplication
            );
            CandidateVerificationOutcome outcome = verificationService.verify(summary).getFirst();
            String actualStatus = outcome.verification().status().name();
            boolean actualFormalAdmission = !eligiblePaperFilter.filter(List.of(outcome), 1).isEmpty();
            boolean statusMatch = expectedStatus.equals(actualStatus);
            boolean admissionMatch = expectedFormalAdmission == actualFormalAdmission;
            boolean falseVerified = VerificationResult.VerificationStatus.VERIFIED.name().equals(actualStatus)
                    && !"VERIFIED".equals(expectedStatus);
            boolean falseFormalAdmission = actualFormalAdmission && !expectedFormalAdmission;
            boolean falseFormalExclusion = !actualFormalAdmission && expectedFormalAdmission;
            return new CaseResult(
                    caseId, split, expectedStatus, actualStatus, statusMatch,
                    expectedFormalAdmission, actualFormalAdmission, admissionMatch,
                    falseVerified, falseFormalAdmission, falseFormalExclusion, legacyFormalResultEligible,
                    outcome.verification().referenceDoi(), outcome.verification().reasons(), null
            );
        } catch (RuntimeException exception) {
            return new CaseResult(
                    caseId, split, expectedStatus, null, false,
                    expectedFormalAdmission, null, false,
                    false, false, false, legacyFormalResultEligible,
                    null, List.of(), "EVALUATION_EXCEPTION_" + exception.getClass().getSimpleName()
            );
        }
    }

    private static String splitName(String caseId, Set<String> calibrationIds, Set<String> acceptanceIds) {
        if (calibrationIds.contains(caseId)) return "CALIBRATION";
        if (acceptanceIds.contains(caseId)) return "ACCEPTANCE";
        throw new IllegalStateException("Reviewed case is not assigned to the frozen split: " + caseId);
    }

    private static int count(List<CaseResult> results, String split, java.util.function.Predicate<CaseResult> filter) {
        return (int) results.stream()
                .filter(result -> split.equals(result.split()))
                .filter(result -> filter == null || filter.test(result))
                .count();
    }

    private static void requireEqual(List<String> failures, String metric, int actual, int required) {
        if (actual != required) failures.add(metric + "_" + actual + "_REQUIRED_" + required);
    }

    private static void requireAtMost(List<String> failures, String metric, int actual, int maximum) {
        if (actual > maximum) failures.add(metric + "_" + actual + "_MAX_" + maximum);
    }

    private static String markdown(BenchmarkReport report) {
        StringBuilder value = new StringBuilder();
        value.append("# Crossref Policy Benchmark v0.1\n\n")
                .append("- Dataset: `").append(report.datasetVersion()).append("`\n")
                .append("- Production baseline: `").append(report.productionBaselineCommit()).append("`\n")
                .append("- Result: **").append(report.acceptancePassed() ? "PASS" : "FAIL").append("**\n")
                .append("- Status matches: ").append(report.overallStatusMatchCount())
                .append('/').append(report.evaluatedCaseCount()).append("\n")
                .append("- Frozen acceptance matches: ").append(report.acceptanceStatusMatchCount())
                .append('/').append(report.acceptanceCaseCount()).append("\n")
                .append("- False VERIFIED: ").append(report.falseVerifiedCount()).append("\n")
                .append("- False formal admission: ").append(report.falseFormalAdmissionCount()).append("\n")
                .append("- False formal exclusion: ").append(report.falseFormalExclusionCount()).append("\n")
                .append("- Exceptions: ").append(report.exceptionCount()).append("\n\n")
                .append("## Failure reasons\n\n");
        if (report.failureReasons().isEmpty()) {
            value.append("- None\n");
        } else {
            report.failureReasons().forEach(reason -> value.append("- `").append(reason).append("`\n"));
        }
        value.append("\n## Case results\n\n")
                .append("| Case | Split | Expected | Actual | Admitted | False VERIFIED | Error |\n")
                .append("|---|---|---|---|---:|---:|---|\n");
        report.caseResults().stream().sorted(Comparator.comparing(CaseResult::caseId)).forEach(result -> value
                .append('|').append(result.caseId())
                .append('|').append(result.split())
                .append('|').append(result.expectedStatus())
                .append('|').append(result.actualStatus() == null ? "UNKNOWN" : result.actualStatus())
                .append('|').append(result.actualFormalAdmission() == null ? "UNKNOWN" : result.actualFormalAdmission())
                .append('|').append(result.falseVerified())
                .append('|').append(result.errorCode() == null ? "" : result.errorCode())
                .append("|\n"));
        return value.toString();
    }

    record BenchmarkReport(
            String schemaVersion,
            String benchmarkVersion,
            String datasetVersion,
            String splitVersion,
            String productionBaselineCommit,
            String policyClass,
            String formalAdmissionOracle,
            String legacyFormalResultEligibleHandling,
            int evaluatedCaseCount,
            int calibrationCaseCount,
            int acceptanceCaseCount,
            int overallStatusMatchCount,
            int calibrationStatusMatchCount,
            int acceptanceStatusMatchCount,
            int formalAdmissionMatchCount,
            int falseVerifiedCount,
            int falseFormalAdmissionCount,
            int falseFormalExclusionCount,
            int exceptionCount,
            boolean acceptancePassed,
            List<String> failureReasons,
            List<CaseResult> caseResults
    ) {
    }

    record CaseResult(
            String caseId,
            String split,
            String expectedStatus,
            String actualStatus,
            boolean statusMatch,
            boolean expectedFormalAdmission,
            Boolean actualFormalAdmission,
            boolean admissionMatch,
            boolean falseVerified,
            boolean falseFormalAdmission,
            boolean falseFormalExclusion,
            boolean legacyFormalResultEligible,
            String actualReferenceDoi,
            List<String> policyReasons,
            String errorCode
    ) {
    }
}
