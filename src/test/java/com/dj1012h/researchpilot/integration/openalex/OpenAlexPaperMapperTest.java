package com.dj1012h.researchpilot.integration.openalex;

import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexAuthorshipDTO;
import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexLocationDTO;
import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorkDTO;
import com.dj1012h.researchpilot.integration.openalex.dto.OpenAlexWorksResponse;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAlexPaperMapperTest {

    private final OpenAlexPaperMapper mapper = new OpenAlexPaperMapper();

    @Test
    void shouldMapNormalPaperAndPreferBestOpenAccessLocation() throws IOException {
        CandidatePaper paper = mapper.map(readFixture()).getFirst();

        assertThat(paper.title())
                .isEqualTo("Highly accurate protein structure prediction with AlphaFold");
        assertThat(paper.sourceName()).isEqualTo("Nature");
        assertThat(paper.publicationDate()).isEqualTo(LocalDate.of(2021, 8, 26));
        assertThat(paper.publicationYear()).isEqualTo(2021);
        assertThat(paper.workType()).isEqualTo("article");
        assertThat(paper.language()).isEqualTo("en");
        assertThat(paper.citedByCount()).isEqualTo(100);
        assertThat(paper.landingPageUrl()).isEqualTo("https://repository.example/paper");
        assertThat(paper.pdfUrl()).isEqualTo("https://repository.example/paper.pdf");
        assertThat(paper.openAccess()).isTrue();
        assertThat(paper.candidateSource()).isEqualTo(CandidatePaper.CandidateSource.OPENALEX);
    }

    @Test
    void shouldNormalizeDoiOpenAlexIdAndAuthors() throws IOException {
        CandidatePaper paper = mapper.map(readFixture()).getFirst();

        assertThat(paper.openAlexId()).isEqualTo("W3177828909");
        assertThat(paper.doi()).isEqualTo("10.1038/s41586-021-03819-2");
        assertThat(paper.authors()).containsExactly(
                new CandidatePaper.Author("A123", "John Jumper", "0000-0001-2345-6789"),
                new CandidatePaper.Author("A456", "Demis Hassabis", null)
        );
    }

    @Test
    void shouldHandleMissingDoi() {
        CandidatePaper paper = mapper.map(work(null, List.of(), null, null, null));

        assertThat(paper.doi()).isNull();
        assertThat(paper.landingPageUrl()).isNull();
    }

    @Test
    void shouldHandleMissingAuthors() {
        CandidatePaper paper = mapper.map(work("https://doi.org/10.1000/example", null, null, null, null));

        assertThat(paper.authors()).isEmpty();
    }

    @Test
    void shouldSkipAuthorshipWithoutUsableAuthorName() {
        List<OpenAlexAuthorshipDTO> authorships = List.of(
                new OpenAlexAuthorshipDTO(null),
                new OpenAlexAuthorshipDTO(new OpenAlexAuthorshipDTO.Author("A1", " ", null))
        );

        CandidatePaper paper = mapper.map(work(null, authorships, null, null, null));

        assertThat(paper.authors()).isEmpty();
    }

    @Test
    void shouldHandleMissingSourceAndLocations() {
        CandidatePaper paper = mapper.map(work(null, List.of(), null, null, null));

        assertThat(paper.sourceName()).isNull();
        assertThat(paper.pdfUrl()).isNull();
        assertThat(paper.openAccess()).isFalse();
    }

    @Test
    void shouldRestoreAbstractByWordPosition() {
        Map<String, List<Integer>> invertedIndex = Map.of(
                "abstract", List.of(3),
                "an", List.of(2),
                "is", List.of(1),
                "This", List.of(0)
        );

        CandidatePaper paper = mapper.map(work(null, List.of(), null, null, invertedIndex));

        assertThat(paper.abstractText()).isEqualTo("This is an abstract");
    }

    @Test
    void shouldHandleEmptyAbstract() {
        assertThat(mapper.map(work(null, List.of(), null, null, null)).abstractText()).isNull();
        assertThat(mapper.map(work(null, List.of(), null, null, Map.of())).abstractText()).isNull();
    }

    @Test
    void shouldTolerateInvalidOptionalPublicationDate() {
        OpenAlexWorkDTO work = new OpenAlexWorkDTO(
                "W1",
                null,
                "Paper",
                null,
                "not-a-date",
                "article",
                null,
                List.of(),
                null,
                null,
                null
        );

        CandidatePaper paper = mapper.map(work);

        assertThat(paper.publicationDate()).isNull();
        assertThat(paper.publicationYear()).isNull();
    }

    private OpenAlexWorkDTO work(String doi,
                                 List<OpenAlexAuthorshipDTO> authorships,
                                 OpenAlexLocationDTO primary,
                                 OpenAlexLocationDTO bestOpenAccess,
                                 Map<String, List<Integer>> abstractIndex) {
        return new OpenAlexWorkDTO(
                "https://openalex.org/W1",
                doi,
                "  Example   paper  ",
                2026,
                "2026-07-19",
                "article",
                null,
                authorships,
                primary,
                bestOpenAccess,
                abstractIndex
        );
    }

    private OpenAlexWorksResponse readFixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/openalex/works-response.json")) {
            return new ObjectMapper().readValue(input, OpenAlexWorksResponse.class);
        }
    }
}
