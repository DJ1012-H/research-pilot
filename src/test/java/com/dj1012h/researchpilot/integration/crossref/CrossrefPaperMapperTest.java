package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefAuthor;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefDate;
import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkMessage;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefPaperMapperTest {

    private final CrossrefPaperMapper mapper = new CrossrefPaperMapper(new DoiNormalizer());

    @Test
    void shouldMapCompleteMetadataWithPrintDatePriority() {
        CrossrefWorkMessage message = new CrossrefWorkMessage(
                " HTTPS://DOI.ORG/10.1000/EXAMPLE ", List.of(" ", "A title"),
                List.of(new CrossrefAuthor("Zoë", "Šarić", null),
                        new CrossrefAuthor("Ada", null, null),
                        new CrossrefAuthor(null, "Lovelace", null),
                        new CrossrefAuthor(" ", " ", null)),
                date(2020), date(2024), date(2023), date(2022), date(2021),
                List.of(" ", "Journal"), "article", "Publisher");

        CrossrefWorkMetadata metadata = mapper.map(message);

        assertThat(metadata.doi()).isEqualTo("10.1000/example");
        assertThat(metadata.title()).isEqualTo("A title");
        assertThat(metadata.authorNames()).containsExactly("Zoë Šarić", "Ada", "Lovelace");
        assertThat(metadata.publicationYear()).isEqualTo(2023);
        assertThat(metadata.venue()).isEqualTo("Journal");
        assertThat(metadata.workType()).isEqualTo("article");
        assertThat(metadata.publisher()).isEqualTo("Publisher");
    }

    @Test
    void shouldUseOnlineIssuedThenCreatedWhenHigherPriorityDatesAreMissingOrMalformed() {
        assertThat(mapper.map(message(null, date(2024), date(2023), date(2022))).publicationYear()).isEqualTo(2024);
        assertThat(mapper.map(message(null, null, date(2023), date(2022))).publicationYear()).isEqualTo(2023);
        assertThat(mapper.map(message(null, null, null, date(2022))).publicationYear()).isEqualTo(2022);
        assertThat(mapper.map(message(null, null, null, malformedDate())).publicationYear()).isNull();
    }

    @Test
    void shouldMapMissingOptionalFieldsDeterministicallyWithoutMutatingTheInput() {
        CrossrefWorkMessage message = new CrossrefWorkMessage("10.1000/example", List.of(), null,
                null, null, null, null, null, List.of(), " ", " ");

        CrossrefWorkMetadata first = mapper.map(message);
        CrossrefWorkMetadata second = mapper.map(message);

        assertThat(first).isEqualTo(second);
        assertThat(message.title()).isEmpty();
        assertThat(message.author()).isNull();
        assertThat(first.title()).isNull();
        assertThat(first.authorNames()).isEmpty();
        assertThat(first.publicationYear()).isNull();
        assertThat(first.venue()).isNull();
        assertThat(first.workType()).isNull();
        assertThat(first.publisher()).isNull();
    }

    private CrossrefWorkMessage message(CrossrefDate print, CrossrefDate online,
                                        CrossrefDate issued, CrossrefDate created) {
        return new CrossrefWorkMessage("10.1000/example", List.of("A title"), List.of(), null,
                online, print, issued, created, List.of("Journal"), "article", "Publisher");
    }

    private CrossrefDate date(int year) {
        return new CrossrefDate(List.of(List.of(year)));
    }

    private CrossrefDate malformedDate() {
        return new CrossrefDate(List.of(List.of()));
    }
}
