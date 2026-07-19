package com.dj1012h.researchpilot.exception;

import com.dj1012h.researchpilot.common.response.ApiErrorResponse;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexApiException;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexFailureType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAlexExceptionHandlerTest {

    private static final String API_KEY_MARKER = "test-key-must-not-leak";

    @ParameterizedTest
    @MethodSource("failureMappings")
    void shouldMapOpenAlexFailureToExistingSafeErrorResponse(OpenAlexFailureType failureType,
                                                             int expectedStatus,
                                                             String expectedCode) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/literature/search");
        OpenAlexApiException exception = new OpenAlexApiException(
                failureType,
                "provider message " + API_KEY_MARKER
        );

        ResponseEntity<ApiErrorResponse> response =
                new GlobalExceptionHandler().handleOpenAlex(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(expectedCode);
        assertThat(response.getBody().message()).doesNotContain(API_KEY_MARKER);
    }

    private static Stream<Arguments> failureMappings() {
        return Stream.of(
                Arguments.of(OpenAlexFailureType.DISABLED, 503, "OPENALEX_NOT_CONFIGURED"),
                Arguments.of(OpenAlexFailureType.API_KEY_MISSING, 503, "OPENALEX_NOT_CONFIGURED"),
                Arguments.of(OpenAlexFailureType.TIMEOUT, 504, "OPENALEX_TIMEOUT"),
                Arguments.of(OpenAlexFailureType.RATE_LIMITED, 503, "OPENALEX_RATE_LIMITED"),
                Arguments.of(OpenAlexFailureType.CLIENT_ERROR, 502, "OPENALEX_REQUEST_REJECTED"),
                Arguments.of(OpenAlexFailureType.SERVER_ERROR, 503, "OPENALEX_UNAVAILABLE"),
                Arguments.of(OpenAlexFailureType.TRANSPORT_ERROR, 503, "OPENALEX_UNAVAILABLE"),
                Arguments.of(OpenAlexFailureType.EMPTY_RESPONSE, 502, "OPENALEX_INVALID_RESPONSE"),
                Arguments.of(OpenAlexFailureType.INVALID_RESPONSE, 502, "OPENALEX_INVALID_RESPONSE")
        );
    }
}
