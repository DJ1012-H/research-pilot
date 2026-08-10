package com.dj1012h.researchpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.dj1012h.researchpilot.integration.crossref.VerificationThresholdProperties;
import com.dj1012h.researchpilot.literature.application.BibliographicSimilarityCalculator;
import com.dj1012h.researchpilot.literature.application.CrossrefVerificationEvidenceService;
import com.dj1012h.researchpilot.literature.application.VerificationEvidenceService;
import com.dj1012h.researchpilot.literature.model.FieldMatchStatus;
import com.dj1012h.researchpilot.literature.model.FieldVerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationField;
import com.dj1012h.researchpilot.literature.normalization.AuthorNormalizer;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CrossrefFieldEvidenceCalibrationTest {

    @Autowired
    private VerificationEvidenceService evidenceService;

    @Autowired
    private VerificationThresholdProperties productionThresholds;

    @Autowired
    private DoiNormalizer doiNormalizer;

    @Autowired
    private TitleNormalizer titleNormalizer;

    @Autowired
    private AuthorNormalizer authorNormalizer;

    @Autowired
    private VenueNormalizer venueNormalizer;

    @Autowired
    private BibliographicSimilarityCalculator similarityCalculator;

    @Test
    void shouldUseTheMergedProductionDefaultThresholdsForCalibration() {
        assertThat(productionThresholds).isEqualTo(
                new VerificationThresholdProperties(0.92, 0.85, 0.60, 0.85, 1));
    }

    @Test
    void shouldRecordCalibrationFieldEvidenceAgainstHumanReviewedRationales() throws Exception {
        CrossrefVerificationDatasetSupport.CalibrationSplit split = CrossrefVerificationDatasetSupport.readSplit();
        List<FieldObservation> observations = evaluate(evidenceService, split.calibrationCaseIds());

        assertThat(observations).hasSize(50);
        assertThat(discrepancies(observations)).containsExactly(
                discrepancy("crv1-case-0012", VerificationField.VENUE,
                        FieldMatchStatus.MATCH, FieldMatchStatus.MISMATCH, "SOURCE_SIMILARITY_BELOW_THRESHOLD"),
                discrepancy("crv1-case-0014", VerificationField.VENUE,
                        FieldMatchStatus.MATCH, FieldMatchStatus.MISMATCH, "SOURCE_SIMILARITY_BELOW_THRESHOLD")
        );
        assertThat(observation(observations, "crv1-case-0012", VerificationField.VENUE).score())
                .isEqualTo(5.0 / 7.0);
    }

    @Test
    void shouldRecordFrozenAcceptanceOutcomeWithoutTuningAgainstIt() throws Exception {
        CrossrefVerificationDatasetSupport.CalibrationSplit split = CrossrefVerificationDatasetSupport.readSplit();
        List<FieldObservation> observations = evaluate(evidenceService, split.acceptanceCaseIds());

        assertThat(observations).hasSize(20);
        assertThat(discrepancies(observations)).containsExactly(
                discrepancy("crv1-case-0013", VerificationField.VENUE,
                        FieldMatchStatus.MATCH, FieldMatchStatus.MISMATCH, "SOURCE_SIMILARITY_BELOW_THRESHOLD")
        );
        assertThat(observation(observations, "crv1-case-0013", VerificationField.YEAR).actual())
                .isEqualTo(FieldMatchStatus.EXPLAINABLE_DIFFERENCE);
    }

    @Test
    void shouldEvaluateOnlyTheApprovedFiniteThresholdCandidatesOnCalibrationCases() throws Exception {
        CrossrefVerificationDatasetSupport.CalibrationSplit split = CrossrefVerificationDatasetSupport.readSplit();

        for (double titleStrongMatch : List.of(0.90, 0.92, 0.94)) {
            assertThat(evaluate(serviceFor(titleStrongMatch, 0.85, 0.60, 0.85, 1), split.calibrationCaseIds()))
                    .hasSize(50);
        }
        for (double titlePossibleMatch : List.of(0.82, 0.85, 0.88)) {
            assertThat(evaluate(serviceFor(0.92, titlePossibleMatch, 0.60, 0.85, 1), split.calibrationCaseIds()))
                    .hasSize(50);
        }
        for (double authorOverlap : List.of(0.50, 0.60, 0.70)) {
            assertThat(evaluate(serviceFor(0.92, 0.85, authorOverlap, 0.85, 1), split.calibrationCaseIds()))
                    .hasSize(50);
        }
        for (double sourceMatch : List.of(0.80, 0.85, 0.90)) {
            List<FieldObservation> observations = evaluate(serviceFor(0.92, 0.85, 0.60, sourceMatch, 1),
                    split.calibrationCaseIds());
            assertThat(observation(observations, "crv1-case-0012", VerificationField.VENUE).actual())
                    .isEqualTo(FieldMatchStatus.MISMATCH);
        }
        assertThat(observation(evaluate(serviceFor(0.92, 0.85, 0.60, 0.85, 0), split.acceptanceCaseIds()),
                "crv1-case-0013", VerificationField.YEAR).actual()).isEqualTo(FieldMatchStatus.MISMATCH);
        assertThat(observation(evaluate(serviceFor(0.92, 0.85, 0.60, 0.85, 1), split.acceptanceCaseIds()),
                "crv1-case-0013", VerificationField.YEAR).actual())
                .isEqualTo(FieldMatchStatus.EXPLAINABLE_DIFFERENCE);
    }

    private List<FieldObservation> evaluate(VerificationEvidenceService service, List<String> caseIds) throws Exception {
        Map<String, JsonNode> cases = CrossrefVerificationDatasetSupport.casesById();
        List<FieldObservation> observations = new ArrayList<>();
        for (String caseId : caseIds) {
            JsonNode caseNode = cases.get(caseId);
            if (caseNode == null) throw new IllegalArgumentException("Unresolved split case: " + caseId);
            VerificationEvidence evidence = service.compare(
                    CrossrefVerificationCaseAdapter.candidate(caseNode),
                    CrossrefVerificationCaseAdapter.reference(caseNode)
            );
            Map<VerificationField, FieldVerificationEvidence> actual = evidence.fieldEvidence().stream()
                    .collect(Collectors.toMap(FieldVerificationEvidence::field, Function.identity()));
            for (Map.Entry<VerificationField, FieldMatchStatus> expected
                    : CrossrefVerificationDatasetSupport.expectedFieldStatuses(caseNode).entrySet()) {
                FieldVerificationEvidence actualField = actual.get(expected.getKey());
                if (actualField == null) {
                    throw new IllegalStateException("Missing generated field evidence: " + expected.getKey());
                }
                observations.add(new FieldObservation(
                        caseId,
                        expected.getKey(),
                        expected.getValue(),
                        actualField.status(),
                        actualField.score(),
                        actualField.explanation()
                ));
            }
        }
        return List.copyOf(observations);
    }

    private VerificationEvidenceService serviceFor(
            double titleStrongMatch,
            double titlePossibleMatch,
            double authorOverlap,
            double sourceMatch,
            int publicationYearTolerance
    ) {
        return new CrossrefVerificationEvidenceService(
                doiNormalizer,
                titleNormalizer,
                authorNormalizer,
                venueNormalizer,
                similarityCalculator,
                new VerificationThresholdProperties(
                        titleStrongMatch,
                        titlePossibleMatch,
                        authorOverlap,
                        sourceMatch,
                        publicationYearTolerance
                )
        );
    }

    private static List<Discrepancy> discrepancies(List<FieldObservation> observations) {
        return observations.stream()
                .filter(observation -> observation.expected() != observation.actual())
                .map(observation -> discrepancy(
                        observation.caseId(), observation.field(), observation.expected(), observation.actual(), observation.reason()))
                .toList();
    }

    private static FieldObservation observation(
            List<FieldObservation> observations,
            String caseId,
            VerificationField field
    ) {
        return observations.stream()
                .filter(observation -> observation.caseId().equals(caseId) && observation.field() == field)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing observation: " + caseId + " / " + field));
    }

    private static Discrepancy discrepancy(
            String caseId,
            VerificationField field,
            FieldMatchStatus expected,
            FieldMatchStatus actual,
            String reason
    ) {
        return new Discrepancy(caseId, field, expected, actual, reason);
    }

    private record FieldObservation(
            String caseId,
            VerificationField field,
            FieldMatchStatus expected,
            FieldMatchStatus actual,
            Double score,
            String reason
    ) {
    }

    private record Discrepancy(
            String caseId,
            VerificationField field,
            FieldMatchStatus expected,
            FieldMatchStatus actual,
            String reason
    ) {
    }
}
