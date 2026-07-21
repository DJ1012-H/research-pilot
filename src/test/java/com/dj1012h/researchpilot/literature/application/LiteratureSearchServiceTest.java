package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LiteratureSearchServiceTest {

    private final SearchAgent searchAgent = mock(SearchAgent.class);
    private final OpenAlexQueryFactory queryFactory = mock(OpenAlexQueryFactory.class);
    private final OpenAlexSearchPort openAlexSearchPort = mock(OpenAlexSearchPort.class);
    private final CrossrefCandidateLookupService crossrefCandidateLookupService = mock(CrossrefCandidateLookupService.class);
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC);
    private final LiteratureSearchService service =
            new LiteratureSearchService(searchAgent, queryFactory, openAlexSearchPort, crossrefCandidateLookupService, clock);

    @Test
    void shouldExecuteOneTrustedOpenAlexSearchWithoutPublishingUnverifiedCandidates() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", null, null, 10);
        SearchPlan plan = plan(request);
        OpenAlexQuery query = query();
        CandidatePaper candidate = candidate();
        when(searchAgent.createPlan(request)).thenReturn(plan);
        when(queryFactory.create(plan)).thenReturn(query);
        when(openAlexSearchPort.search(query))
                .thenReturn(new OpenAlexSearchResult(42, List.of(candidate), null));
        when(crossrefCandidateLookupService.lookup(List.of(candidate))).thenReturn(disabledSummary(1));

        SearchResponse response = service.search(request);

        assertThat(response.status()).isEqualTo(SearchResponse.SearchStatus.NO_VERIFIED_RESULTS);
        assertThat(response.plan()).isSameAs(plan);
        assertThat(response.candidateCount()).isOne();
        assertThat(response.deduplicatedCount()).isZero();
        assertThat(response.verificationSummary().totalCount()).isZero();
        assertThat(response.papers()).isEmpty();
        assertThat(response.message()).contains("尚未执行字段级核验");
        assertThat(response.elapsedMs()).isZero();
        assertThat(response.completedAt()).isEqualTo(Instant.parse("2026-07-20T08:00:00Z"));
        assertThat(response.taskId()).isNotNull();
        verify(searchAgent).createPlan(request);
        verify(queryFactory).create(plan);
        verify(openAlexSearchPort).search(query);
        verify(crossrefCandidateLookupService).lookup(List.of(candidate));
        verifyNoMoreInteractions(openAlexSearchPort);
    }

    @Test
    void shouldDescribeEmptyCandidateResultWithoutAddingFormalPapers() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", null, null, 10);
        SearchPlan plan = plan(request);
        OpenAlexQuery query = query();
        when(searchAgent.createPlan(request)).thenReturn(plan);
        when(queryFactory.create(plan)).thenReturn(query);
        when(openAlexSearchPort.search(query))
                .thenReturn(new OpenAlexSearchResult(0, List.of(), null));
        when(crossrefCandidateLookupService.lookup(List.of())).thenReturn(disabledSummary(0));

        SearchResponse response = service.search(request);

        assertThat(response.candidateCount()).isZero();
        assertThat(response.papers()).isEmpty();
        assertThat(response.message()).contains("未检索到候选论文");
    }

    private CrossrefLookupSummary disabledSummary(int eligible) {
        return new CrossrefLookupSummary(eligible, 0, 0, 0, 0, 0, false, false, List.of());
    }

    private SearchPlan plan(SearchRequest request) {
        return new SearchPlan(
                request.query(),
                "Mamba remote sensing change detection",
                List.of("Mamba", "remote sensing", "change detection"),
                "Mamba remote sensing change detection",
                Set.of(LanguageCode.EN),
                List.of("article"),
                SearchSort.NEWEST,
                2022,
                2026,
                30,
                10
        );
    }

    private OpenAlexQuery query() {
        return new OpenAlexQuery(
                "Mamba remote sensing change detection",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of("article"),
                List.of("en"),
                OpenAlexQuery.Sort.NEWEST,
                30
        );
    }

    private CandidatePaper candidate() {
        return new CandidatePaper(
                "W1",
                "10.1000/example",
                "Example paper",
                List.of(),
                "Example Journal",
                LocalDate.of(2026, 7, 1),
                2026,
                "article",
                "en",
                10,
                "Abstract",
                "https://doi.org/10.1000/example",
                null,
                false,
                CandidatePaper.CandidateSource.OPENALEX
        );
    }
}
