package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
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

class VerificationPolicyTest {

    private final VerificationPolicy policy = new VerificationPolicy(new DoiNormalizer());

    @Test
    void shouldVerifyExactNormalizedDoiWhenThereIsNoHardConflict() {
        CrossrefWorkMetadata reference = reference("10.1000/example");
        VerificationResult result = policy.evaluate(found("https://doi.org/10.1000/EXAMPLE", List.of(reference)),
                List.of(evidence(FieldMatchStatus.MATCH, FieldMatchStatus.MATCH, FieldMatchStatus.MATCH)));

        assertThat(result.status()).isEqualTo(VerificationResult.VerificationStatus.VERIFIED);
        assertThat(result.referenceDoi()).isEqualTo("10.1000/example");
    }

    @Test
    void shouldRejectExactDoiWhenTitleIsAConfirmedConflict() {
        CrossrefWorkMetadata reference = reference("10.1000/example");
        VerificationResult result = policy.evaluate(found("10.1000/example", List.of(reference)),
                List.of(evidence(FieldMatchStatus.MATCH, FieldMatchStatus.MISMATCH, FieldMatchStatus.MATCH)));

        assertThat(result.status()).isEqualTo(VerificationResult.VerificationStatus.CONFLICTED);
    }

    @Test
    void shouldRequireOneStrongBibliographicReferenceForNoDoiCandidate() {
        CrossrefWorkMetadata first = reference("10.1000/a");
        CrossrefWorkMetadata second = reference("10.1000/b");
        VerificationResult result = policy.evaluate(found(null, List.of(first, second)), List.of(
                evidence(FieldMatchStatus.MISSING_FROM_CANDIDATE, FieldMatchStatus.MATCH, FieldMatchStatus.MATCH),
                evidence(FieldMatchStatus.MISSING_FROM_CANDIDATE, FieldMatchStatus.MATCH, FieldMatchStatus.MATCH)));

        assertThat(result.status()).isEqualTo(VerificationResult.VerificationStatus.PARTIALLY_VERIFIED);
        assertThat(result.referenceDoi()).isNull();
    }

    @Test
    void shouldPreserveLookupFailuresAsNonRejectingStatuses() {
        CandidateLookupResult notFound = lookup(null, CandidateLookupResult.LookupStatus.NOT_FOUND, List.of());
        CandidateLookupResult unavailable = lookup(null, CandidateLookupResult.LookupStatus.SOURCE_UNAVAILABLE, List.of());

        assertThat(policy.evaluate(notFound, List.of()).status())
                .isEqualTo(VerificationResult.VerificationStatus.NOT_FOUND);
        assertThat(policy.evaluate(unavailable, List.of()).status())
                .isEqualTo(VerificationResult.VerificationStatus.SOURCE_UNAVAILABLE);
    }

    private CandidateLookupResult found(String doi, List<CrossrefWorkMetadata> references) {
        return lookup(doi, CandidateLookupResult.LookupStatus.FOUND, references);
    }

    private CandidateLookupResult lookup(
            String doi, CandidateLookupResult.LookupStatus status, List<CrossrefWorkMetadata> references
    ) {
        CandidatePaper candidate = new CandidatePaper("W1", doi, "Title", List.of(), "Venue", null, 2024,
                "article", "en", 0, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
        NormalizedCandidate normalized = new NormalizedCandidate("W1", candidate,
                new DoiNormalizer().normalize(doi), "W1", "title", null, 2024, "venue", 0);
        return new CandidateLookupResult(normalized,
                doi == null ? CandidateLookupResult.LookupRoute.BIBLIOGRAPHIC : CandidateLookupResult.LookupRoute.DOI,
                status, references, "TEST");
    }

    private VerificationEvidence evidence(
            FieldMatchStatus doi, FieldMatchStatus title, FieldMatchStatus firstAuthor
    ) {
        return new VerificationEvidence("W1", "CROSSREF", List.of(
                field(VerificationField.DOI, doi), field(VerificationField.TITLE, title),
                field(VerificationField.FIRST_AUTHOR, firstAuthor), field(VerificationField.AUTHORS, firstAuthor),
                field(VerificationField.YEAR, FieldMatchStatus.MATCH), field(VerificationField.VENUE, FieldMatchStatus.MATCH)
        ));
    }

    private FieldVerificationEvidence field(VerificationField field, FieldMatchStatus status) {
        return new FieldVerificationEvidence(field, "candidate", "reference", status,
                status == FieldMatchStatus.MATCH ? 1.0 : null, "TEST");
    }

    private CrossrefWorkMetadata reference(String doi) {
        return new CrossrefWorkMetadata(doi, "Title", List.of(), 2024, "Venue", "article", "Publisher");
    }
}
