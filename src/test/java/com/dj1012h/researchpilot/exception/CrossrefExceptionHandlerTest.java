package com.dj1012h.researchpilot.exception;

import com.dj1012h.researchpilot.common.response.ApiErrorResponse;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefFailureType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefExceptionHandlerTest {

    @ParameterizedTest
    @MethodSource("failureMappings")
    void shouldMapCrossrefFailuresToSafeResponses(CrossrefFailureType type, int status, String code) {
        ResponseEntity<ApiErrorResponse> response = new GlobalExceptionHandler().handleCrossref(
                new CrossrefApiException(type, "secret@example.com token-value"),
                new MockHttpServletRequest("POST", "/api/literature/search")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).doesNotContain("secret@example.com").doesNotContain("token-value");
    }

    private static Stream<Arguments> failureMappings() {
        return Stream.of(
                Arguments.of(CrossrefFailureType.MAILTO_MISSING, 503, "CROSSREF_NOT_CONFIGURED"),
                Arguments.of(CrossrefFailureType.TIMEOUT, 504, "CROSSREF_TIMEOUT"),
                Arguments.of(CrossrefFailureType.RATE_LIMITED, 503, "CROSSREF_RATE_LIMITED"),
                Arguments.of(CrossrefFailureType.FORBIDDEN, 502, "CROSSREF_REQUEST_REJECTED"),
                Arguments.of(CrossrefFailureType.SERVER_ERROR, 503, "CROSSREF_UNAVAILABLE"),
                Arguments.of(CrossrefFailureType.INVALID_RESPONSE, 502, "CROSSREF_INVALID_RESPONSE")
        );
    }
}
