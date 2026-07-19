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
}
