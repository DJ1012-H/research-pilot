package com.dj1012h.researchpilot.literature.rag.answer;

import com.dj1012h.researchpilot.exception.GlobalExceptionHandler;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.observability.RequestCorrelation;
import com.dj1012h.researchpilot.observability.RequestCorrelationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP and serialization characterization for the public ask boundary. */
class RagAnswerControllerHttpContractTest {

    private RagAnswerService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RagAnswerService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RagAnswerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestCorrelationFilter())
                .build();
    }

    @Test
    void shouldReturnSuccessWithRequestCorrelationAndOnlyPublicAnswerFields() throws Exception {
        when(service.answer(any())).thenAnswer(ignored -> success(RequestCorrelation.requestIdOrNew()));

        MvcResult mvcResult = mockMvc.perform(post("/api/research/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"state space models\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists(RequestCorrelationFilter.RESPONSE_HEADER))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.answer").value("A bounded answer.\n"))
                .andExpect(jsonPath("$.citations[0].paperId").value(101))
                .andExpect(jsonPath("$.citations[0].normalizedDoi").value("10.1000/answer"))
                .andExpect(jsonPath("$.diagnostics.modelCallCount").value(2))
                .andExpect(jsonPath("$.diagnostics.relevanceJudgeCallCount").value(1))
                .andExpect(jsonPath("$.diagnostics.answerModelCallCount").value(1))
                .andExpect(jsonPath("$.diagnostics.admittedEvidenceCount").value(1))
                .andExpect(jsonPath("$.diagnostics.generationEvidenceCount").value(1))
                .andReturn()
                ;

        String body = mvcResult.getResponse().getContentAsString();
        String headerRequestId = mvcResult.getResponse().getHeader(RequestCorrelationFilter.RESPONSE_HEADER);
        JsonNode responseJson = new ObjectMapper().readTree(body);

        assertThat(body)
                .contains("\"requestId\"")
                .doesNotContain("reconstructedSegmentText", "rawContent", "prompt", "modelDraft", "TrustedRagEvidence");
        assertThat(headerRequestId).isEqualTo(responseJson.path("requestId").asText());
        assertThat(headerRequestId).isNotBlank();
        verify(service).answer(any(ResearchQuestionRequest.class));
    }

    @Test
    void shouldKeepInsufficientEvidenceContract() throws Exception {
        when(service.answer(any())).thenAnswer(ignored -> insufficient(RequestCorrelation.requestIdOrNew()));

        mockMvc.perform(post("/api/research/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"no evidence\",\"fromYear\":2099,\"toYear\":2100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.answer").value(""))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.diagnostics.modelCallCount").value(0))
                .andExpect(jsonPath("$.diagnostics.relevanceJudgeCallCount").value(0))
                .andExpect(jsonPath("$.diagnostics.answerModelCallCount").value(0))
                .andExpect(jsonPath("$.diagnostics.admittedEvidenceCount").value(0))
                .andExpect(jsonPath("$.diagnostics.generationEvidenceCount").value(0))
                .andExpect(jsonPath("$.diagnostics.repairCount").value(0));
    }

    @Test
    void shouldKeepFailedContractWithoutPublishingAnswerOrCitations() throws Exception {
        when(service.answer(any())).thenAnswer(ignored -> failed(RequestCorrelation.requestIdOrNew()));

        mockMvc.perform(post("/api/research/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"provider unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.answer").value(""))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.diagnostics.failureCode").value("RAG_GENERATION_UNAVAILABLE"));
    }

    @Test
    void shouldUseExistingGlobalInvalidJsonBoundary() throws Exception {
        mockMvc.perform(post("/api/research/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.path").value("/api/research/ask"));
    }

    @Test
    void shouldCharacterizeUnknownJsonFieldsWithoutChangingGlobalJacksonConfiguration() throws Exception {
        when(service.answer(any())).thenAnswer(ignored -> insufficient(RequestCorrelation.requestIdOrNew()));

        mockMvc.perform(post("/api/research/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"unknown field\",\"futureControl\":\"ignored by current MVC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"));
    }

    private ResearchAnswerResponse success(UUID requestId) {
        return new ResearchAnswerResponse(
                requestId,
                RagAnswerStatus.SUCCESS,
                "A bounded answer.\n",
                List.of(new RagAnswerCitation(
                        "P1", 1, 101L, "10.1000/answer", "Java-owned title", 2024,
                        "Trusted venue", RagSegmentType.ABSTRACT, 0,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0.9)),
                new RagAnswerRetrievalSummary("test-v1", 5, 2, 2, 1, 1, 1, 0.9),
                false,
                "Citation mapping only.",
                12,
                new RagAnswerDiagnostics(null, 2, 1, 1, 1, 1, 0, 1));
    }

    private ResearchAnswerResponse insufficient(UUID requestId) {
        return new ResearchAnswerResponse(
                requestId,
                RagAnswerStatus.INSUFFICIENT_EVIDENCE,
                "",
                List.of(),
                new RagAnswerRetrievalSummary("test-v1", 5, 2, 2, 0, 0, 2, null),
                true,
                "No re-admitted ABSTRACT evidence.",
                3,
                new RagAnswerDiagnostics("RAG_INSUFFICIENT_EVIDENCE", 0, 0, 0, 0, 0, 0, 0));
    }

    private ResearchAnswerResponse failed(UUID requestId) {
        return new ResearchAnswerResponse(
                requestId,
                RagAnswerStatus.FAILED,
                "",
                List.of(),
                new RagAnswerRetrievalSummary("test-v1", 5, 0, 0, 0, 0, 0, null),
                false,
                "Generation unavailable.",
                4,
                new RagAnswerDiagnostics("RAG_GENERATION_UNAVAILABLE", 2, 1, 1, 1, 1, 0, 0));
    }
}
