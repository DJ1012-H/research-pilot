package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EligiblePaperFilterTest {

    private final EligiblePaperFilter filter = new EligiblePaperFilter(new DoiNormalizer());

    @Test
    void shouldAdmitOnlyVerifiedPapersAndDeduplicateTheirNormalizedDoi() {
        List<SearchResponse.PaperResult> papers = filter.filter(List.of(
                outcome("W1", "10.1000/a", VerificationResult.VerificationStatus.VERIFIED),
                outcome("W2", "https://doi.org/10.1000/A", VerificationResult.VerificationStatus.VERIFIED),
                outcome("W3", "10.1000/c", VerificationResult.VerificationStatus.PARTIALLY_VERIFIED)
        ), 10);

        assertThat(papers).singleElement().satisfies(paper -> {
            assertThat(paper.paper().doi()).isEqualTo("10.1000/a");
            assertThat(paper.paper().source()).isEqualTo(com.dj1012h.researchpilot.literature.model.PaperDTO.LiteratureSource.OPENALEX);
            assertThat(paper.verification().status()).isEqualTo(VerificationResult.VerificationStatus.VERIFIED);
            assertThat(paper.relevanceScore()).isEqualTo(1.0);
        });
    }

    @Test
    void shouldUseOpenAlexMetadataWithoutOverwritingNonEmptyFields() {
        CandidateVerificationOutcome outcome = outcome("W1", "10.1000/a", VerificationResult.VerificationStatus.VERIFIED);

        SearchResponse.PaperResult paper = filter.filter(List.of(outcome), 1).getFirst();

        assertThat(paper.paper().title()).isEqualTo("OpenAlex title W1");
        assertThat(paper.paper().venue()).isEqualTo("OpenAlex venue");
        assertThat(paper.paper().publicationYear()).isEqualTo(2024);
    }

    @Test
    void shouldPreferOpenAlexAbstractOverCrossrefAbstract() {
        SearchResponse.PaperResult paper = filter.filter(List.of(
                outcome("W1", "10.1000/a", VerificationResult.VerificationStatus.VERIFIED,
                        "OpenAlex abstract", "Crossref abstract")
        ), 1).getFirst();

        assertThat(paper.paper().abstractText()).isEqualTo("OpenAlex abstract");
    }

    @Test
    void shouldUseCrossrefAbstractWhenOpenAlexAbstractIsMissing() {
        SearchResponse.PaperResult paper = filter.filter(List.of(
                outcome("W1", "10.1000/a", VerificationResult.VerificationStatus.VERIFIED,
                        null, "Crossref abstract")
        ), 1).getFirst();

        assertThat(paper.paper().abstractText()).isEqualTo("Crossref abstract");
    }

    @Test
    void shouldKeepAbstractNullWhenBothProvidersAreMissing() {
        SearchResponse.PaperResult paper = filter.filter(List.of(
                outcome("W1", "10.1000/a", VerificationResult.VerificationStatus.VERIFIED,
                        null, null)
        ), 1).getFirst();

        assertThat(paper.paper().abstractText()).isNull();
    }

    private CandidateVerificationOutcome outcome(
            String id, String doi, VerificationResult.VerificationStatus status
    ) {
        return outcome(id, doi, status, "abstract", null);
    }

    private CandidateVerificationOutcome outcome(
            String id, String doi, VerificationResult.VerificationStatus status,
            String candidateAbstract, String crossrefAbstract
    ) {
        CandidatePaper candidate = new CandidatePaper(id, null, "OpenAlex title " + id,
                List.of(new CandidatePaper.Author(null, "OpenAlex Author", null)), "OpenAlex venue", null, 2024,
                "article", "en", 3, candidateAbstract, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
        CrossrefWorkMetadata reference = new CrossrefWorkMetadata(doi, "Crossref title", List.of("Crossref Author"),
                2020, "Crossref venue", "journal-article", "Publisher", crossrefAbstract);
        VerificationResult verification = new VerificationResult(status, 1.0,
                VerificationResult.VerificationSource.CROSSREF,
                status == VerificationResult.VerificationStatus.VERIFIED ? doi : null, List.of(), List.of("TEST"));
        return new CandidateVerificationOutcome(candidate,
                status == VerificationResult.VerificationStatus.VERIFIED ? reference : null, verification);
    }
}
