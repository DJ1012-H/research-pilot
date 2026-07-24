package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefFixtureDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeReviewedDoiResponseWithoutLosingUnicodeOrDateParts() throws IOException {
        CrossrefWorkResponse response = objectMapper.readValue(fixtureStream(), CrossrefWorkResponse.class);

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.message().doi()).isEqualTo("10.1038/s41586-021-03819-2");
        assertThat(response.message().title()).contains("Highly accurate protein structure prediction with AlphaFold");
        assertThat(response.message().author()).extracting(author -> author.family()).contains("Jumper");
        assertThat(response.message().publishedPrint().dateParts().getFirst()).containsExactly(2021, 8, 26);
    }

    private InputStream fixtureStream() {
        InputStream stream = getClass().getResourceAsStream("/fixtures/crossref/work-by-doi-success.json");
        if (stream == null) throw new IllegalStateException("Crossref fixture is missing");
        return stream;
    }
}
