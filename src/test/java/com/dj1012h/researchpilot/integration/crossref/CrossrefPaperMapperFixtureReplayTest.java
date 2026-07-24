package com.dj1012h.researchpilot.integration.crossref;

import com.dj1012h.researchpilot.integration.crossref.dto.CrossrefWorkResponse;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefPaperMapperFixtureReplayTest {

    @Test
    void shouldReplayReviewedResponseThroughTheRealMapper() throws IOException {
        CrossrefWorkResponse response = new ObjectMapper().readValue(fixtureStream(), CrossrefWorkResponse.class);
        CrossrefWorkMetadata metadata = new CrossrefPaperMapper(new DoiNormalizer()).map(response.message());

        assertThat(metadata.doi()).isEqualTo("10.1038/s41586-021-03819-2");
        assertThat(metadata.title()).isEqualTo("Highly accurate protein structure prediction with AlphaFold");
        assertThat(metadata.authorNames()).contains("John Jumper");
        assertThat(metadata.publicationYear()).isEqualTo(2021);
        assertThat(metadata.venue()).isEqualTo("Nature");
        assertThat(metadata.workType()).isEqualTo("journal-article");
        assertThat(metadata.publisher()).isEqualTo("Springer Science and Business Media LLC");
    }

    private InputStream fixtureStream() {
        InputStream stream = getClass().getResourceAsStream("/fixtures/crossref/work-by-doi-success.json");
        if (stream == null) throw new IllegalStateException("Crossref fixture is missing");
        return stream;
    }
}
