package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAlexSearchAdapterTest {

    @Mock
    private OpenAlexClient client;

    @Mock
    private OpenAlexPaperMapper mapper;

    @Test
    void shouldHideExternalResponseBehindSearchPort() {
        OpenAlexQuery query = new OpenAlexQuery(
                "query",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(),
                OpenAlexQuery.Sort.RELEVANCE,
                20
        );
        OpenAlexWorksResponse response = new OpenAlexWorksResponse(
                new OpenAlexWorksResponse.Meta(42L, "next-cursor"),
                List.of()
        );
        CandidatePaper candidate = new CandidatePaper(
                "W1",
                null,
                "Example paper",
                List.of(),
                null,
                LocalDate.of(2026, 7, 19),
                2026,
                "article",
                0,
                null,
                null,
                null,
                false,
                CandidatePaper.CandidateSource.OPENALEX
        );
        when(client.search(query)).thenReturn(response);
        when(mapper.map(response)).thenReturn(List.of(candidate));
        OpenAlexSearchPort port = new OpenAlexSearchAdapter(client, mapper);

        OpenAlexSearchResult result = port.search(query);

        assertThat(result.totalMatches()).isEqualTo(42);
        assertThat(result.candidates()).containsExactly(candidate);
        assertThat(result.nextCursor()).isEqualTo("next-cursor");
    }

    @Test
    void shouldRerankNewestCandidatesByPublicationDateAfterRelevanceRetrieval() {
        OpenAlexQuery query = new OpenAlexQuery(
                "CBT-I insomnia",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(),
                OpenAlexQuery.Sort.NEWEST,
                3
        );
        OpenAlexWorksResponse response = new OpenAlexWorksResponse(
                new OpenAlexWorksResponse.Meta(3L, null), List.of());
        CandidatePaper a = candidate("A", LocalDate.of(2024, 1, 1));
        CandidatePaper b = candidate("B", LocalDate.of(2026, 1, 1));
        CandidatePaper c = candidate("C", LocalDate.of(2025, 1, 1));
        when(client.search(query)).thenReturn(response);
        when(mapper.map(response)).thenReturn(List.of(a, b, c));

        OpenAlexSearchResult result = new OpenAlexSearchAdapter(client, mapper).search(query);

        assertThat(result.candidates()).containsExactly(b, c, a);
    }

    @Test
    void shouldKeepProviderOrderForRelevanceQueries() {
        OpenAlexQuery query = new OpenAlexQuery(
                "CBT-I insomnia",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(),
                OpenAlexQuery.Sort.RELEVANCE,
                3
        );
        OpenAlexWorksResponse response = new OpenAlexWorksResponse(
                new OpenAlexWorksResponse.Meta(3L, null), List.of());
        CandidatePaper a = candidate("A", LocalDate.of(2024, 1, 1));
        CandidatePaper b = candidate("B", LocalDate.of(2026, 1, 1));
        CandidatePaper c = candidate("C", LocalDate.of(2025, 1, 1));
        when(client.search(query)).thenReturn(response);
        when(mapper.map(response)).thenReturn(List.of(a, b, c));

        OpenAlexSearchResult result = new OpenAlexSearchAdapter(client, mapper).search(query);

        assertThat(result.candidates()).containsExactly(a, b, c);
    }

    private CandidatePaper candidate(String id, LocalDate publicationDate) {
        return new CandidatePaper(id, null, "Paper " + id, List.of(), null, publicationDate,
                publicationDate.getYear(), "article", 0, null, null, null, false,
                CandidatePaper.CandidateSource.OPENALEX);
    }
}
