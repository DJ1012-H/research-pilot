package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefAuthor;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefDate;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkMessage;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossrefSearchAdapterTest {

    private final CrossrefClient client = mock(CrossrefClient.class);
    private final CrossrefSearchAdapter adapter = new CrossrefSearchAdapter(client);

    @Test
    void shouldMapFoundMetadataWithOnlineDatePriorityAndMissingOptionalFields() {
        when(client.getWorkByDoi("10/example")).thenReturn(new CrossrefWorkResponse("ok", null, null,
                new CrossrefWorkMessage("10/example", List.of("A title"),
                        List.of(new CrossrefAuthor("Ada", "Lovelace", null), new CrossrefAuthor(null, null, "Group")),
                        date(2020), date(2024), date(2023), date(2022), List.of("Journal"), "article", "Publisher")));

        CrossrefLookupResult result = adapter.findByDoi("10/example");

        assertThat(result.status()).isEqualTo(CrossrefLookupResult.Status.FOUND);
        assertThat(result.metadata().publicationYear()).isEqualTo(2024);
        assertThat(result.metadata().authorNames()).containsExactly("Ada Lovelace", "Group");
    }

    @Test
    void shouldConvertOnlyNotFoundFailureToNotFound() {
        when(client.getWorkByDoi("10/missing"))
                .thenThrow(new CrossrefApiException(CrossrefFailureType.NOT_FOUND, "not found"));

        assertThat(adapter.findByDoi("10/missing").status()).isEqualTo(CrossrefLookupResult.Status.NOT_FOUND);
    }

    private CrossrefDate date(int year) { return new CrossrefDate(List.of(List.of(year))); }
}
