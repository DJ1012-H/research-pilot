package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.integration.crossref.VerificationThresholdProperties;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.FieldMatchStatus;
import com.dj1012h.researchpilot.literature.model.FieldVerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationField;
import com.dj1012h.researchpilot.literature.normalization.AuthorNormalizer;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossrefVerificationEvidenceServiceTest {

    private final CrossrefVerificationEvidenceService service = service(
            new VerificationThresholdProperties(0.92, 0.85, 0.60, 0.85, 1));

    @Test
    void shouldProduceFieldEvidenceWithoutDecidingAFinalVerificationStatus() {
        VerificationEvidence evidence = service.compare(
                candidate("https://doi.org/10.1000/example", "Rank‐based detection", authors("Smith, John P", "Doe, Ada"),
                        2023, "Journal of Testing"),
                reference("10.1000/example", "Rank-based detection", List.of("John P Smith", "Ada Doe"),
                        2024, "journal of testing")
        );

        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        assertThat(evidence.evidenceSource()).isEqualTo("CROSSREF");
        assertThat(fields.get(VerificationField.DOI)).extracting(FieldVerificationEvidence::status)
                .isEqualTo(FieldMatchStatus.MATCH);
        assertThat(fields.get(VerificationField.TITLE)).extracting(FieldVerificationEvidence::status)
                .isEqualTo(FieldMatchStatus.MATCH);
        assertThat(fields.get(VerificationField.FIRST_AUTHOR)).extracting(FieldVerificationEvidence::status)
                .isEqualTo(FieldMatchStatus.MATCH);
        assertThat(fields.get(VerificationField.AUTHORS)).extracting(FieldVerificationEvidence::status)
                .isEqualTo(FieldMatchStatus.MATCH);
        assertThat(fields.get(VerificationField.YEAR)).extracting(FieldVerificationEvidence::status)
                .isEqualTo(FieldMatchStatus.EXPLAINABLE_DIFFERENCE);
        assertThat(fields.get(VerificationField.YEAR).explanation())
                .isEqualTo("PUBLICATION_YEAR_WITHIN_TOLERANCE;delta=1");
        assertThat(fields.get(VerificationField.VENUE)).extracting(FieldVerificationEvidence::status)
                .isEqualTo(FieldMatchStatus.MATCH);
    }

    @Test
    void shouldPreserveTitleAndFirstAuthorConflictsEvenWhenDoiMatches() {
        VerificationEvidence evidence = service.compare(
                candidate("10.1000/example", "Different paper title", authors("Jane Doe"), 2024, "Journal"),
                reference("10.1000/example", "Original paper title", List.of("John Smith"), 2024, "Journal")
        );

        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        assertThat(fields.get(VerificationField.DOI).status()).isEqualTo(FieldMatchStatus.MATCH);
        assertThat(fields.get(VerificationField.TITLE).status()).isEqualTo(FieldMatchStatus.MISMATCH);
        assertThat(fields.get(VerificationField.FIRST_AUTHOR).status()).isEqualTo(FieldMatchStatus.MISMATCH);
    }

    @Test
    void shouldRepresentMissingCandidateFieldsWithoutCreatingZeroScores() {
        VerificationEvidence evidence = service.compare(
                candidate(null, null, List.of(), null, null),
                reference("10.1000/example", "Reference title", List.of("John Smith"), 2024, "Journal")
        );

        for (FieldVerificationEvidence field : evidence.fieldEvidence()) {
            assertThat(field.status()).isEqualTo(FieldMatchStatus.MISSING_FROM_CANDIDATE);
            assertThat(field.score()).isNull();
        }
    }

    @Test
    void shouldRepresentBothMissingComparableFieldsSeparatelyFromMatchesOrConflicts() {
        VerificationEvidence evidence = service.compare(
                candidate(null, null, List.of(), null, null),
                reference("10.1000/example", null, List.of(), null, null)
        );

        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        for (VerificationField field : List.of(
                VerificationField.TITLE,
                VerificationField.FIRST_AUTHOR,
                VerificationField.AUTHORS,
                VerificationField.YEAR,
                VerificationField.VENUE
        )) {
            assertThat(fields.get(field).status()).isEqualTo(FieldMatchStatus.MISSING_FROM_BOTH);
            assertThat(fields.get(field).score()).isNull();
            assertThat(fields.get(field).explanation()).isEqualTo("BOTH_VALUES_MISSING");
        }
    }

    @Test
    void shouldReportDoiConflictWithoutFuzzyMatching() {
        VerificationEvidence evidence = service.compare(
                candidate("10.1000/candidate", "Title", authors("John Smith"), 2024, "Journal"),
                reference("10.1000/reference", "Title", List.of("John Smith"), 2024, "Journal")
        );

        FieldVerificationEvidence doi = fields(evidence).get(VerificationField.DOI);
        assertThat(doi.status()).isEqualTo(FieldMatchStatus.MISMATCH);
        assertThat(doi.score()).isEqualTo(0.0);
        assertThat(doi.explanation()).isEqualTo("DOI_CONFLICT");
    }

    @Test
    void shouldKeepTheExistingReferenceDoiInvariant() {
        assertThatThrownBy(() -> reference(" ", "Title", List.of(), 2024, "Journal"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldClassifyTitleThresholdBoundariesUsingRawScores() {
        VerificationThresholdProperties thresholds = new VerificationThresholdProperties(0.92, 0.85, 0.60, 0.85, 1);

        assertThat(CrossrefVerificationEvidenceService.classifyTitleScore(0.92, thresholds))
                .isEqualTo(FieldMatchStatus.MATCH);
        assertThat(CrossrefVerificationEvidenceService.classifyTitleScore(Math.nextDown(0.92), thresholds))
                .isEqualTo(FieldMatchStatus.NOT_EVALUATED);
        assertThat(CrossrefVerificationEvidenceService.classifyTitleScore(Math.nextUp(0.92), thresholds))
                .isEqualTo(FieldMatchStatus.MATCH);
        assertThat(CrossrefVerificationEvidenceService.classifyTitleScore(0.85, thresholds))
                .isEqualTo(FieldMatchStatus.NOT_EVALUATED);
        assertThat(CrossrefVerificationEvidenceService.classifyTitleScore(Math.nextDown(0.85), thresholds))
                .isEqualTo(FieldMatchStatus.MISMATCH);
    }

    @Test
    void shouldRecordPossibleTitleAsInconclusiveInsteadOfExplainedDifference() {
        CrossrefVerificationEvidenceService possibleTitleService = service(
                new VerificationThresholdProperties(1.0, 0.0, 0.60, 0.85, 1));

        VerificationEvidence evidence = possibleTitleService.compare(
                candidate("10.1000/example", "Candidate title", authors("John Smith"), 2024, "Journal"),
                reference("10.1000/example", "Reference title", List.of("John Smith"), 2024, "Journal")
        );

        FieldVerificationEvidence title = fields(evidence).get(VerificationField.TITLE);
        assertThat(title.status()).isEqualTo(FieldMatchStatus.NOT_EVALUATED);
        assertThat(title.explanation()).isEqualTo("TITLE_SIMILARITY_POSSIBLE");
    }

    @Test
    void shouldKeepFirstAuthorAndAuthorSetAsIndependentEvidence() {
        VerificationEvidence differentRemainingAuthors = service.compare(
                candidate("10.1000/example", "Title", authors("John Smith", "Ada One"), 2024, "Journal"),
                reference("10.1000/example", "Title", List.of("John Smith", "Bob Two"), 2024, "Journal")
        );
        Map<VerificationField, FieldVerificationEvidence> fields = fields(differentRemainingAuthors);

        assertThat(fields.get(VerificationField.FIRST_AUTHOR).status()).isEqualTo(FieldMatchStatus.MATCH);
        assertThat(fields.get(VerificationField.AUTHORS).status()).isEqualTo(FieldMatchStatus.MISMATCH);
        assertThat(fields.get(VerificationField.AUTHORS).score()).isEqualTo(1.0 / 3.0);
    }

    @Test
    void shouldDeduplicateAndIgnoreOrderForAuthorSetOverlap() {
        VerificationEvidence evidence = service.compare(
                candidate("10.1000/example", "Title", authors("John Smith", "Ada Doe", "John Smith"), 2024, "Journal"),
                reference("10.1000/example", "Title", List.of("Ada Doe", "John Smith"), 2024, "Journal")
        );

        FieldVerificationEvidence authors = fields(evidence).get(VerificationField.AUTHORS);
        assertThat(authors.status()).isEqualTo(FieldMatchStatus.MATCH);
        assertThat(authors.score()).isEqualTo(1.0);
    }

    @Test
    void shouldRepresentMissingReferenceFieldsWithoutCreatingZeroScores() {
        VerificationEvidence evidence = service.compare(
                candidate("10.1000/example", "Candidate title", authors("John Smith"), 2024, "Journal"),
                reference("10.1000/example", null, List.of(), null, null)
        );

        Map<VerificationField, FieldVerificationEvidence> fields = fields(evidence);
        for (VerificationField field : List.of(
                VerificationField.TITLE,
                VerificationField.FIRST_AUTHOR,
                VerificationField.AUTHORS,
                VerificationField.YEAR,
                VerificationField.VENUE
        )) {
            assertThat(fields.get(field).status()).isEqualTo(FieldMatchStatus.MISSING_FROM_EVIDENCE);
            assertThat(fields.get(field).score()).isNull();
        }
    }

    private static Map<VerificationField, FieldVerificationEvidence> fields(VerificationEvidence evidence) {
        return evidence.fieldEvidence().stream()
                .collect(java.util.stream.Collectors.toMap(FieldVerificationEvidence::field, field -> field));
    }

    private static CrossrefVerificationEvidenceService service(VerificationThresholdProperties thresholds) {
        return new CrossrefVerificationEvidenceService(
                new DoiNormalizer(),
                new TitleNormalizer(),
                new AuthorNormalizer(),
                new VenueNormalizer(),
                new BibliographicSimilarityCalculator(new TitleNormalizer(), new VenueNormalizer()),
                thresholds
        );
    }

    private static CandidatePaper candidate(
            String doi,
            String title,
            List<CandidatePaper.Author> authors,
            Integer year,
            String venue
    ) {
        return new CandidatePaper(
                "W1", doi, title, authors, venue, LocalDate.of(2024, 1, 1), year,
                "article", 0, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX
        );
    }

    private static List<CandidatePaper.Author> authors(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> new CandidatePaper.Author(null, name, null))
                .toList();
    }

    private static CrossrefWorkMetadata reference(
            String doi,
            String title,
            List<String> authors,
            Integer year,
            String venue
    ) {
        return new CrossrefWorkMetadata(doi, title, authors, year, venue, "journal-article", "Publisher");
    }
}
