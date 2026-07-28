package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchActionDraftMapperTest {

    @Test
    void shouldStrictlyMapTheSingleActionField() throws Exception {
        SearchActionDraftMapper mapper = new SearchActionDraftMapper(new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper()));

        assertThat(mapper.map(JsonMapper.builder().build().readTree("{\"action\":\"COMPLETE\"}")))
                .isEqualTo(new SearchActionDraft("COMPLETE"));
    }
}
