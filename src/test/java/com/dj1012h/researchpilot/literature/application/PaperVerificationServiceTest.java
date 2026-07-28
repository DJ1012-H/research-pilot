package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidateLookupResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.FieldMatchStatus;
import com.dj1012h.researchpilot.literature.model.FieldVerificationEvidence;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.model.VerificationEvidence;
import com.dj1012h.researchpilot.literature.model.VerificationField;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperVerificationServiceTest {

    private final VerificationEvidenceService evidenceService = mock(VerificationEvidenceService.class);
    private final PaperVerificationService service = new PaperVerificationService(
            evidenceService, new VerificationPolicy(new DoiNormalizer()), new DoiNormalizer());

    @Test
    void shouldCompareEveryFoundReferenceAndRetainUniqueStrongSelection() {
        CandidatePaper candidate = candidate();
        NormalizedCandidate normalized = new NormalizedCandidate("W1", candidate, null, "W1", "title", null,
                2024, "venue", 0);
        CrossrefWorkMetadata matching = reference("10.1000/match");
        CrossrefWorkMetadata conflicting = reference("10.1000/conflict");
        CandidateLookupResult lookup = new CandidateLookupResult(normalized,
                CandidateLookupResult.LookupRoute.BIBLIOGRAPHIC, CandidateLookupResult.LookupStatus.FOUND,
                List.of(matching, conflicting), "TEST");
        when(evidenceService.compare(candidate, matching)).thenReturn(strongEvidence());
        when(evidenceService.compare(candidate, conflicting)).thenReturn(conflictingEvidence());

        var outcomes = service.verify(summary(normalized, lookup));

        assertThat(outcomes).singleElement().satisfies(outcome -> {
            assertThat(outcome.verification().status()).isEqualTo(VerificationResult.VerificationStatus.VERIFIED);
            assertThat(outcome.selectedReference()).isSameAs(matching);
        });
        verify(evidenceService).compare(candidate, matching);
        verify(evidenceService).compare(candidate, conflicting);
    }

    @Test
    void shouldNotCompareWhenLookupWasNotFound() {
        CandidatePaper candidate = candidate();
        NormalizedCandidate normalized = new NormalizedCandidate("W1", candidate, null, "W1", "title", null,
                2024, "venue", 0);
        CandidateLookupResult lookup = new CandidateLookupResult(normalized,
                CandidateLookupResult.LookupRoute.BIBLIOGRAPHIC, CandidateLookupResult.LookupStatus.NOT_FOUND,
                List.of(), "TEST");

        assertThat(service.verify(summary(normalized, lookup))).singleElement()
                .extracting(outcome -> outcome.verification().status())
                .isEqualTo(VerificationResult.VerificationStatus.NOT_FOUND);
    }

    private CrossrefLookupSummary summary(NormalizedCandidate candidate, CandidateLookupResult lookup) {
        CandidateDeduplicationResult deduplication = new CandidateDeduplicationResult(
                List.of(candidate), List.of(), 1, 1, 0);
        return new CrossrefLookupSummary(0, 1, 1, 1, 0, 0, 0, true, true,
                lookup.references(), List.of(), List.of(lookup), deduplication);
    }

    private CandidatePaper candidate() {
        return new CandidatePaper("W1", null, "Title", List.of(), "Venue", null, 2024,
                "article", "en", 0, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
    }

    private CrossrefWorkMetadata reference(String doi) {
        return new CrossrefWorkMetadata(doi, "Title", List.of(), 2024, "Venue", "article", "Publisher");
    }

    private VerificationEvidence strongEvidence() {
        return evidence(FieldMatchStatus.MATCH, FieldMatchStatus.MATCH);
    }

    private VerificationEvidence conflictingEvidence() {
        return evidence(FieldMatchStatus.MISMATCH, FieldMatchStatus.MISMATCH);
    }

    private VerificationEvidence evidence(FieldMatchStatus title, FieldMatchStatus other) {
        return new VerificationEvidence("W1", "CROSSREF", List.of(
                field(VerificationField.DOI, FieldMatchStatus.MISSING_FROM_CANDIDATE),
                field(VerificationField.TITLE, title), field(VerificationField.FIRST_AUTHOR, other),
                field(VerificationField.AUTHORS, other), field(VerificationField.YEAR, other),
                field(VerificationField.VENUE, other)));
    }

    private FieldVerificationEvidence field(VerificationField field, FieldMatchStatus status) {
        return new FieldVerificationEvidence(field, "candidate", "reference", status,
                status == FieldMatchStatus.MATCH ? 1.0 : null, "TEST");
    }
}
