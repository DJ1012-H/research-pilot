package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefAuthor;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefDate;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkMessage;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorksResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossrefSearchAdapterTest {

    private final CrossrefClient client = mock(CrossrefClient.class);
    private final CrossrefSearchAdapter adapter = new CrossrefSearchAdapter(client, paperMapper());

    @Test
    void shouldMapFoundMetadataWithPrintDatePriorityAndMissingOptionalFields() {
        when(client.getWorkByDoi("10.1000/example")).thenReturn(new CrossrefWorkResponse("ok", null, null,
                new CrossrefWorkMessage("HTTPS://DOI.ORG/10.1000/EXAMPLE", List.of("A title"),
                        List.of(new CrossrefAuthor("Ada", "Lovelace", null), new CrossrefAuthor(null, null, "Group")),
                        date(2020), date(2024), date(2023), date(2022), List.of("Journal"), "article", "Publisher")));

        CrossrefLookupResult result = adapter.findByDoi("10.1000/example");

        assertThat(result.status()).isEqualTo(CrossrefLookupResult.Status.FOUND);
        assertThat(result.metadata().doi()).isEqualTo("10.1000/example");
        assertThat(result.metadata().publicationYear()).isEqualTo(2023);
        assertThat(result.metadata().authorNames()).containsExactly("Ada Lovelace", "Group");
    }

    @Test
    void shouldConvertOnlyNotFoundFailureToNotFound() {
        when(client.getWorkByDoi("10.1000/missing"))
                .thenThrow(new CrossrefApiException(CrossrefFailureType.NOT_FOUND, "not found"));

        assertThat(adapter.findByDoi("10.1000/missing").status()).isEqualTo(CrossrefLookupResult.Status.NOT_FOUND);
    }

    @Test
    void shouldKeepSingleAndMultipleBibliographicCandidatesDistinct() {
        CrossrefBibliographicQuery query = new CrossrefBibliographicQuery("A title", "Ada", 2026, "Journal");
        when(client.getWorksByBibliographic(query)).thenReturn(new com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorksResponse(
                "ok", null, null, new com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorksResponse.CrossrefWorksMessage(List.of(
                        new CrossrefWorkMessage("10.1000/a", List.of("A"), List.of(), null, null, null, null,
                                List.of("Journal"), "article", null),
                        new CrossrefWorkMessage("10.1000/b", List.of("B"), List.of(), null, null, null, null,
                                List.of("Journal"), "article", null)
                ))));

        CrossrefBibliographicLookupResult result = adapter.findByBibliographic(query);

        assertThat(result.status()).isEqualTo(CrossrefBibliographicLookupResult.Status.FOUND_MULTIPLE);
        assertThat(result.candidates()).extracting(CrossrefWorkMetadata::doi).containsExactly("10.1000/a", "10.1000/b");
    }

    @Test
    void shouldRepresentSingleEmptyAndNotFoundBibliographicResultsWithoutSelectingACandidate() {
        CrossrefBibliographicQuery singleQuery = new CrossrefBibliographicQuery("Single", null, null, null);
        CrossrefBibliographicQuery emptyQuery = new CrossrefBibliographicQuery("Empty", null, null, null);
        CrossrefBibliographicQuery missingQuery = new CrossrefBibliographicQuery("Missing", null, null, null);
        when(client.getWorksByBibliographic(singleQuery)).thenReturn(works(List.of(work("10.1000/single", "Single"))));
        when(client.getWorksByBibliographic(emptyQuery)).thenReturn(works(List.of()));
        when(client.getWorksByBibliographic(missingQuery))
                .thenThrow(new CrossrefApiException(CrossrefFailureType.NOT_FOUND, "not found"));

        assertThat(adapter.findByBibliographic(singleQuery).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.FOUND_SINGLE);
        assertThat(adapter.findByBibliographic(emptyQuery).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.NOT_FOUND);
        assertThat(adapter.findByBibliographic(missingQuery).status())
                .isEqualTo(CrossrefBibliographicLookupResult.Status.NOT_FOUND);
    }

    @Test
    void shouldRejectInvalidBibliographicItemsInsteadOfPromotingThemToFound() {
        CrossrefBibliographicQuery query = new CrossrefBibliographicQuery("Invalid", null, null, null);
        when(client.getWorksByBibliographic(query)).thenReturn(works(List.of(work("invalid-doi", "Invalid"))));

        assertThatThrownBy(() -> adapter.findByBibliographic(query))
                .isInstanceOfSatisfying(CrossrefApiException.class,
                        exception -> assertThat(exception.getFailureType())
                                .isEqualTo(CrossrefFailureType.INVALID_RESPONSE));
    }

    @Test
    void shouldRejectInvalidResponseDoi() {
        when(client.getWorkByDoi("10.1000/example")).thenReturn(new CrossrefWorkResponse("ok", null, null,
                new CrossrefWorkMessage("10.invalid/example", List.of("A title"), List.of(),
                        null, null, null, null, List.of(), "article", "Publisher")));

        assertThatThrownBy(() -> adapter.findByDoi("10.1000/example"))
                .isInstanceOfSatisfying(CrossrefApiException.class,
                        exception -> assertThat(exception.getFailureType())
                                .isEqualTo(CrossrefFailureType.INVALID_RESPONSE));
    }

    @ParameterizedTest
    @EnumSource(value = CrossrefFailureType.class, names = {
            "INVALID_RESPONSE", "TIMEOUT", "TRANSPORT_ERROR", "DISABLED"
    })
    void shouldNotDisguiseControlledFailuresAsNotFound(CrossrefFailureType failureType) {
        when(client.getWorkByDoi("10.1000/example"))
                .thenThrow(new CrossrefApiException(failureType, "controlled failure"));

        assertThatThrownBy(() -> adapter.findByDoi("10.1000/example"))
                .isInstanceOfSatisfying(CrossrefApiException.class,
                        exception -> assertThat(exception.getFailureType()).isEqualTo(failureType));
    }

    private CrossrefDate date(int year) { return new CrossrefDate(List.of(List.of(year))); }

    private CrossrefPaperMapper paperMapper() {
        return new CrossrefPaperMapper(new com.dj1012h.researchpilot.literature.normalization.DoiNormalizer());
    }

    private CrossrefWorksResponse works(List<CrossrefWorkMessage> items) {
        return new CrossrefWorksResponse("ok", null, null,
                new CrossrefWorksResponse.CrossrefWorksMessage(items));
    }

    private CrossrefWorkMessage work(String doi, String title) {
        return new CrossrefWorkMessage(doi, List.of(title), List.of(), null, null, null, null,
                List.of("Journal"), "article", null);
    }
}
