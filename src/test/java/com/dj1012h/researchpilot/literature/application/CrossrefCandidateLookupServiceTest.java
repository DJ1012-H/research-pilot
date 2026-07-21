package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefFailureType;
import com.dj1012h.researchpilot.integration.crossref.CrossrefLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefSearchPort;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossrefCandidateLookupServiceTest {

    private final CrossrefSearchPort port = mock(CrossrefSearchPort.class);
    private final CrossrefProperties crossref = new CrossrefProperties();
    private final LiteratureSearchProperties search = new LiteratureSearchProperties();
    private final CrossrefCandidateLookupService service = new CrossrefCandidateLookupService(port, crossref, search);

    @Test
    void shouldKeepStableDistinctDoiOrderAndRespectBudget() {
        crossref.setEnabled(true);
        search.setMaxCrossrefLookupsPerRequest(2);
        when(port.findByDoi("10/a")).thenReturn(CrossrefLookupResult.found(metadata("10/a")));
        when(port.findByDoi("10/b")).thenReturn(CrossrefLookupResult.notFound());

        CrossrefLookupSummary summary = service.lookup(List.of(candidate("10/a"), candidate(" "), candidate("10/a"), candidate("10/b"), candidate("10/c")));

        assertThat(summary.doiEligibleCount()).isEqualTo(3);
        assertThat(summary.attemptedCount()).isEqualTo(2);
        assertThat(summary.foundCount()).isOne();
        assertThat(summary.notFoundCount()).isOne();
        assertThat(summary.skippedByLimitCount()).isOne();
        verify(port).findByDoi("10/a");
        verify(port).findByDoi("10/b");
    }

    @Test
    void shouldStopForDisabledOrSourceUnavailableButContinueAfterNotFound() {
        CrossrefLookupSummary disabled = service.lookup(List.of(candidate("10/a")));
        assertThat(disabled.crossrefEnabled()).isFalse();
        verify(port, never()).findByDoi("10/a");

        crossref.setEnabled(true);
        when(port.findByDoi("10/a")).thenReturn(CrossrefLookupResult.notFound());
        when(port.findByDoi("10/b")).thenThrow(new CrossrefApiException(CrossrefFailureType.TIMEOUT, "timeout"));
        CrossrefLookupSummary unavailable = service.lookup(List.of(candidate("10/a"), candidate("10/b"), candidate("10/c")));
        assertThat(unavailable.notFoundCount()).isOne();
        assertThat(unavailable.failedCount()).isOne();
        assertThat(unavailable.sourceAvailable()).isFalse();
        verify(port, never()).findByDoi("10/c");
    }

    private CandidatePaper candidate(String doi) {
        return new CandidatePaper("id-" + doi, doi, "title", List.of(), null, null, null, null, null, 0, null, null, null, false, CandidatePaper.CandidateSource.OPENALEX);
    }

    private CrossrefWorkMetadata metadata(String doi) {
        return new CrossrefWorkMetadata(doi, "title", List.of(), null, null, null, null);
    }
}
