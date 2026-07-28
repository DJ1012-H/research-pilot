package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefFailureType;
import com.dj1012h.researchpilot.integration.crossref.CrossrefLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefSearchPort;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.DeduplicationReason;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CrossrefCandidateLookupServiceTest {

    private final CrossrefSearchPort port = mock(CrossrefSearchPort.class);
    private final CrossrefProperties crossref = new CrossrefProperties();
    private final LiteratureSearchProperties search = new LiteratureSearchProperties();
    private final CrossrefCandidateLookupService service = new CrossrefCandidateLookupService(
            port, crossref, search, new DoiNormalizer(), new CrossrefTitleQueryGuard());

    @Test
    void shouldKeepDeduplicatedCandidateOrderAndShareBudgetWithTitles() {
        crossref.setEnabled(true);
        search.setMaxCrossrefLookupsPerRequest(2);
        when(port.findByDoi("10.1000/a")).thenReturn(CrossrefLookupResult.found(metadata("10.1000/a")));
        when(port.findByBibliographic(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CrossrefBibliographicLookupResult.found(List.of(metadata("10.1000/title"))));

        CrossrefLookupSummary summary = service.lookup(List.of(
                candidate(null, "No DOI title"), candidate("10.1000/a", "First DOI"),
                candidate("https://doi.org/10.1000/a", "Duplicate DOI"), candidate("10.1000/b", "Second DOI")));

        assertThat(summary.doiEligibleCount()).isEqualTo(2);
        assertThat(summary.titleEligibleCount()).isOne();
        assertThat(summary.attemptedCount()).isEqualTo(2);
        assertThat(summary.candidateDeduplication().inputCount()).isEqualTo(4);
        assertThat(summary.candidateDeduplication().uniqueCount()).isEqualTo(3);
        assertThat(summary.candidateDeduplication().removedCount()).isOne();
        assertThat(summary.foundCount()).isEqualTo(2);
        assertThat(summary.notFoundCount()).isZero();
        assertThat(summary.skippedByLimitCount()).isOne();
        assertThat(summary.candidateResults()).hasSize(3);
        assertThat(summary.candidateResults())
                .extracting(result -> result.status())
                .containsExactly(
                        com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.FOUND,
                        com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.FOUND,
                        com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.SKIPPED_BY_LIMIT);
        verify(port).findByBibliographic(org.mockito.ArgumentMatchers.any());
        verify(port).findByDoi("10.1000/a");
        verify(port, never()).findByDoi("10.1000/b");
    }

    @Test
    void shouldQueryOnlyTwoUniqueCandidatesFromFiveOriginalCandidates() {
        crossref.setEnabled(true);
        when(port.findByDoi("10.1000/a")).thenReturn(CrossrefLookupResult.notFound());
        when(port.findByBibliographic(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CrossrefBibliographicLookupResult.notFound());

        CrossrefLookupSummary summary = service.lookup(List.of(
                identityCandidate("W100", "10.1000/a", "DOI paper", "Jane Doe"),
                identityCandidate("W101", "doi:10.1000/A", "DOI duplicate", "J. Doe"),
                identityCandidate(null, null, "Exact Study", "Jane Doe"),
                identityCandidate(null, null, " exact study ", "Jane Doe"),
                identityCandidate(null, null, "Exact Study", "Jane Doe")
        ));

        assertThat(summary.candidateDeduplication().inputCount()).isEqualTo(5);
        assertThat(summary.candidateDeduplication().uniqueCount()).isEqualTo(2);
        assertThat(summary.candidateDeduplication().removedCount()).isEqualTo(3);
        assertThat(summary.candidateDeduplication().duplicateGroups()).hasSize(2);
        assertThat(summary.candidateDeduplication().duplicateGroups())
                .extracting(group -> group.reason())
                .containsExactlyInAnyOrder(
                        DeduplicationReason.SAME_NORMALIZED_DOI,
                        DeduplicationReason.SAME_EXACT_BIBLIOGRAPHIC_KEY
                );
        int representedOriginals = summary.candidateDeduplication().uniqueCount()
                + summary.candidateDeduplication().duplicateGroups().stream()
                .mapToInt(group -> group.removedCandidateIds().size())
                .sum();
        assertThat(representedOriginals).isEqualTo(5);
        assertThat(summary.attemptedCount()).isEqualTo(2);
        verify(port).findByDoi("10.1000/a");
        verify(port).findByBibliographic(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUseBibliographicRouteForValidNoDoiTitleAndKeepAllCandidates() {
        crossref.setEnabled(true);
        when(port.findByBibliographic(org.mockito.ArgumentMatchers.any())).thenReturn(
                CrossrefBibliographicLookupResult.found(List.of(metadata("10.1000/a"), metadata("10.1000/b"))));

        CrossrefLookupSummary summary = service.lookup(List.of(candidate(null,
                "  Mamba:\nRemote\tSensing Change Detection (2026)  ")));

        ArgumentCaptor<com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicQuery> query =
                ArgumentCaptor.forClass(com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicQuery.class);
        verify(port).findByBibliographic(query.capture());
        assertThat(query.getValue().title()).isEqualTo("Mamba: Remote Sensing Change Detection (2026)");
        assertThat(summary.foundMetadata()).hasSize(2);
        assertThat(summary.candidateResults()).singleElement()
                .extracting(result -> result.references())
                .isEqualTo(List.of(metadata("10.1000/a"), metadata("10.1000/b")));
        assertThat(summary.bibliographicResults()).singleElement()
                .extracting(CrossrefBibliographicLookupResult::status)
                .isEqualTo(CrossrefBibliographicLookupResult.Status.FOUND_MULTIPLE);
        verify(port, never()).findByDoi(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotFallbackToTitleWhenAnExactDoiIsNotFound() {
        crossref.setEnabled(true);
        when(port.findByDoi("10.1000/missing")).thenReturn(CrossrefLookupResult.notFound());

        CrossrefLookupSummary summary = service.lookup(List.of(candidate("10.1000/missing", "A usable title")));

        assertThat(summary.notFoundCount()).isOne();
        assertThat(summary.titleEligibleCount()).isZero();
        verify(port, never()).findByBibliographic(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldMakeNoPortCallWhenCrossrefIsDisabled() {
        CrossrefLookupSummary summary = service.lookup(List.of(
                candidate("10.1000/a", "DOI title"), candidate(null, "Eligible title")));

        assertThat(summary.crossrefEnabled()).isFalse();
        assertThat(summary.doiEligibleCount()).isOne();
        assertThat(summary.titleEligibleCount()).isOne();
        assertThat(summary.attemptedCount()).isZero();
        assertThat(summary.candidateResults()).hasSize(2)
                .extracting(result -> result.status())
                .containsOnly(com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.SOURCE_DISABLED);
        verifyNoInteractions(port);
    }

    @Test
    void shouldDeduplicateEquivalentNormalizedBibliographicQueries() {
        crossref.setEnabled(true);
        when(port.findByBibliographic(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CrossrefBibliographicLookupResult.notFound());

        CrossrefLookupSummary summary = service.lookup(List.of(
                candidate(null, "Mamba Remote Sensing"), candidate(null, "  Mamba\nRemote\tSensing  ")));

        assertThat(summary.titleEligibleCount()).isOne();
        assertThat(summary.attemptedCount()).isOne();
        assertThat(summary.notFoundCount()).isOne();
        verify(port).findByBibliographic(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectEveryInvalidTitleBeforeAnyPortCall() {
        crossref.setEnabled(true);

        CrossrefLookupSummary summary = service.lookup(List.of(
                candidate(null, null), candidate(null, "   "), candidate(null, "?!"),
                candidate(null, "https://example.invalid/work"), candidate(null, "{\"title\":\"x\"}"),
                candidate(null, "<?xml version=\"1.0\"?><work>x</work>"),
                candidate(null, "```json\n{}\n```"), candidate(null, "a".repeat(33))));

        assertThat(summary.titleEligibleCount()).isZero();
        assertThat(summary.attemptedCount()).isZero();
        verifyNoInteractions(port);
    }

    @Test
    void shouldSkipInvalidTitlesBeforeThePortAndStopAfterSourceUnavailable() {
        crossref.setEnabled(true);
        when(port.findByBibliographic(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new CrossrefApiException(CrossrefFailureType.TIMEOUT, "timeout"));

        CrossrefLookupSummary summary = service.lookup(List.of(
                candidate(null, "https://not-a-paper.example"), candidate(null, "A valid title"),
                candidate(null, "Later valid title")));

        assertThat(summary.titleEligibleCount()).isEqualTo(2);
        assertThat(summary.failedCount()).isOne();
        assertThat(summary.sourceAvailable()).isFalse();
        assertThat(summary.candidateResults())
                .extracting(result -> result.status())
                .containsExactly(
                        com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.NOT_ELIGIBLE,
                        com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.SOURCE_UNAVAILABLE,
                        com.dj1012h.researchpilot.literature.model.CandidateLookupResult.LookupStatus.SOURCE_UNAVAILABLE);
        verify(port).findByBibliographic(org.mockito.ArgumentMatchers.any());
    }

    private CandidatePaper candidate(String doi, String title) {
        return new CandidatePaper("id-" + title, doi, title, List.of(), "Journal", null, 2026, null,
                null, 0, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
    }

    private CandidatePaper identityCandidate(String openAlexId, String doi, String title, String firstAuthor) {
        return new CandidatePaper(openAlexId, doi, title,
                List.of(new CandidatePaper.Author(null, firstAuthor, null)),
                "Journal", null, 2026, "article", "en", 0,
                null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
    }

    private CrossrefWorkMetadata metadata(String doi) {
        return new CrossrefWorkMetadata(doi, "title", List.of(), null, null, null, null);
    }
}
