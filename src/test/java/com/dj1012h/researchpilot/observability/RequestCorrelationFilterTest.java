package com.dj1012h.researchpilot.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class RequestCorrelationFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldGenerateServerOwnedRequestIdAndClearMdcBetweenRequests(CapturedOutput output) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new RequestCorrelationFilter())
                .build();

        String first = mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestCorrelationFilter.RESPONSE_HEADER))
                .andReturn().getResponse().getHeader(RequestCorrelationFilter.RESPONSE_HEADER);
        assertThat(MDC.get(RequestCorrelation.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelation.TASK_ID_KEY)).isNull();

        String second = mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestCorrelationFilter.RESPONSE_HEADER);
        assertThat(second).isNotEqualTo(first);
        assertThat(MDC.get(RequestCorrelation.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelation.TASK_ID_KEY)).isNull();
        assertThat(output).contains("event=http_request_completed requestId=" + first)
                .contains("event=http_request_completed requestId=" + second);
    }

    @Controller
    static class TestController {
        @GetMapping("/test")
        ResponseEntity<Void> test() {
            return ResponseEntity.ok().build();
        }
    }
}
