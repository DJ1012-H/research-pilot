package com.dj1012h.researchpilot.literature.api;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import com.dj1012h.researchpilot.literature.api.dto.PublicTerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.ReviewCitation;
import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.LiteratureSearchService;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ai.enabled=false",
        "app.openalex.enabled=false",
        "spring.data.redis.host=localhost"
})
@AutoConfigureMockMvc
class LiteratureSearchMvcSerializationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mvcObjectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private LiteratureSearchService literatureSearchService;

    @Test
    void shouldKeepStrictStructuredMapperIsolatedFromSpringMvcMapper() throws Exception {
        Map<String, ObjectMapper> mapperBeans =
                applicationContext.getBeansOfType(ObjectMapper.class);

        assertThat(mapperBeans)
                .containsOnlyKeys("jacksonObjectMapper")
                .containsValue(mvcObjectMapper);
        assertThat(applicationContext.getBean(StructuredOutputConfiguration.OBJECT_MAPPER_BEAN))
                .isInstanceOf(StructuredOutputMapper.class);
        assertThat(mvcObjectMapper.writeValueAsString(
                Map.of("timestamp", Instant.parse("2026-07-20T08:00:00Z"))
        )).isEqualTo("{\"timestamp\":\"2026-07-20T08:00:00Z\"}");
    }

    @Test
    void shouldSerializeCompletedAtOnSuccessfulSearchResponse() throws Exception {
        when(literatureSearchService.search(any())).thenReturn(response());

        mockMvc.perform(post("/api/literature/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"recent Mamba remote sensing papers\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("NO_VERIFIED_RESULTS"))
                .andExpect(jsonPath("$.review.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.review.summary").value(""))
                .andExpect(jsonPath("$.review.citations").isEmpty())
                .andExpect(jsonPath("$.terminationReason").value("NO_VERIFIED_RESULTS"))
                .andExpect(jsonPath("$.terminationDetail").doesNotExist())
                .andExpect(jsonPath("$.completedAt").value("2026-07-20T08:00:00Z"));
    }

    @Test
    void shouldNotSerializeAbstractTextInPublicPaperResults() throws Exception {
        when(literatureSearchService.search(any())).thenReturn(responseWithFormalPaper());

        String payload = mockMvc.perform(post("/api/literature/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"recent Mamba remote sensing papers\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papers[0].paper.doi").value("10.1000/public-paper"))
                .andExpect(jsonPath("$.papers[0].paper.abstractText").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(payload)
                .doesNotContain("\"abstractText\"", "Sensitive full abstract");
    }

    @Test
    void shouldSerializeTimestampOnHandledErrorResponse() throws Exception {
        when(literatureSearchService.search(any()))
                .thenThrow(new ModelNotConfiguredException("not configured for test"));

        mockMvc.perform(post("/api/literature/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"recent Mamba remote sensing papers\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MODEL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value("/api/literature/search"));
    }

    @Test
    void shouldSerializeOnlyTheStableGeneratedReviewContract() throws Exception {
        ReviewResponse generated = new ReviewResponse(
                ReviewResponse.ReviewStatus.GENERATED,
                "A bounded observation. [P3]",
                List.of(new ReviewCitation(
                        "P3",
                        3,
                        "10.1000/java-source",
                        "Java-owned title",
                        List.of("Java-owned author"),
                        2025,
                        "Java-owned venue"
                )),
                "Citation mapping validated; semantic support is not proven."
        );

        String json = mvcObjectMapper.writeValueAsString(generated);

        assertThat(json)
                .contains(
                        "\"status\":\"GENERATED\"",
                        "\"citationId\":\"P3\"",
                        "\"formalPaperPosition\":3",
                        "\"doi\":\"10.1000/java-source\""
                )
                .doesNotContain(
                        "Sensitive abstract",
                        "UntrustedReviewDraft",
                        "ReviewInput",
                        "ReviewDraft",
                        "AgentState",
                        "terminationDetail",
                        "rawContent"
                );
    }

    private SearchResponse response() {
        SearchPlan plan = new SearchPlan(
                "recent Mamba remote sensing papers",
                "Mamba remote sensing",
                List.of("Mamba", "remote sensing"),
                "Mamba remote sensing",
                Set.of(LanguageCode.EN),
                List.of("article"),
                SearchSort.NEWEST,
                2022,
                2026,
                30,
                10
        );
        return new SearchResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                SearchResponse.SearchStatus.NO_VERIFIED_RESULTS,
                plan,
                0,
                0,
                new SearchResponse.VerificationSummary(0, 0, 0, 0),
                List.of(),
                new ReviewResponse(
                        ReviewResponse.ReviewStatus.INSUFFICIENT_EVIDENCE,
                        "",
                        List.of(),
                        "Insufficient verified abstract evidence."
                ),
                PublicTerminationReason.NO_VERIFIED_RESULTS,
                "No verified results",
                25,
                Instant.parse("2026-07-20T08:00:00Z")
        );
    }

    private SearchResponse responseWithFormalPaper() {
        SearchResponse emptyResponse = response();
        PaperDTO paper = new PaperDTO(
                "https://openalex.org/W1",
                "10.1000/public-paper",
                "Public paper",
                List.of(new PaperDTO.Author("https://openalex.org/A1", "Public author", null)),
                2025,
                "Public venue",
                List.of("1234-5678"),
                "article",
                "https://doi.org/10.1000/public-paper",
                "Sensitive full abstract",
                "en",
                List.of("remote sensing"),
                3,
                PaperDTO.LiteratureSource.OPENALEX
        );
        VerificationResult verification = new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED,
                1.0,
                VerificationResult.VerificationSource.CROSSREF,
                paper.doi(),
                List.of(),
                List.of("verified for serialization test")
        );

        return new SearchResponse(
                emptyResponse.taskId(),
                SearchResponse.SearchStatus.PARTIAL_SUCCESS,
                emptyResponse.plan(),
                1,
                1,
                new SearchResponse.VerificationSummary(1, 0, 0, 0),
                List.of(new SearchResponse.PaperResult(paper, 1.0, verification)),
                emptyResponse.review(),
                PublicTerminationReason.PARTIAL_RESULTS,
                "One verified paper",
                emptyResponse.elapsedMs(),
                emptyResponse.completedAt()
        );
    }
}
