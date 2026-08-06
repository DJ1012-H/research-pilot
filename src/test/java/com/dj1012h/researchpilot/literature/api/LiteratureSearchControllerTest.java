package com.dj1012h.researchpilot.literature.api;

import com.dj1012h.researchpilot.exception.GlobalExceptionHandler;
import com.dj1012h.researchpilot.literature.api.dto.PublicTerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.ReviewResponse;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.LiteratureSearchService;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiteratureSearchControllerTest {

    private LiteratureSearchService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LiteratureSearchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LiteratureSearchController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldValidateAndDelegateLiteratureSearch() throws Exception {
        SearchResponse response = response();
        when(service.search(any())).thenReturn(response);

        mockMvc.perform(post("/api/literature/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Mamba 遥感变化检测",
                                  "fromYear": 2022,
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_VERIFIED_RESULTS"))
                .andExpect(jsonPath("$.plan.sort").value("NEWEST"))
                .andExpect(jsonPath("$.candidateCount").value(0))
                .andExpect(jsonPath("$.review.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.terminationReason").value("NO_VERIFIED_RESULTS"))
                .andExpect(jsonPath("$.papers").isArray());

        verify(service).search(new SearchRequest("Mamba 遥感变化检测", 2022, null, 10));
    }

    @Test
    void shouldRejectInvalidRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/literature/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"valid query\",\"limit\":16}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(service, never()).search(any());
    }

    private SearchResponse response() {
        SearchPlan plan = new SearchPlan(
                "Mamba 遥感变化检测",
                "Mamba remote sensing change detection",
                List.of("Mamba", "remote sensing"),
                "Mamba remote sensing change detection",
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
                "未检索到候选论文",
                25,
                Instant.parse("2026-07-20T08:00:00Z")
        );
    }
}
