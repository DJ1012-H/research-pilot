package com.dj1012h.researchpilot.literature.review;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitationIdParserTest {

    private final CitationIdParser parser = new CitationIdParser();

    @ParameterizedTest
    @ValueSource(strings = {"P1", "P2", "P999"})
    void shouldParseOnlyCanonicalCitationIds(String value) {
        assertThat(parser.parse(value, "$.citationId").value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "P0", "P-1", "P01", "p1", "P 1", "[P1]", "P1,P2", " P1", "P1 ",
            "10.1000/example", "P999999999999999999999999"
    })
    void shouldRejectNonCanonicalCitationIds(String value) {
        assertThatThrownBy(() -> parser.parse(value, "$.citationId"))
                .isInstanceOfSatisfying(ReviewDraftValidationException.class, exception ->
                        assertThat(exception.safeCodes()).containsExactly("MALFORMED_CITATION_ID"));
    }
}
