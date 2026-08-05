package com.dj1012h.researchpilot.exception;

import com.dj1012h.researchpilot.common.response.ApiErrorResponse;
import com.dj1012h.researchpilot.controller.ChatController;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationException;
import com.dj1012h.researchpilot.literature.persistence.LiteraturePersistenceException;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import com.dj1012h.researchpilot.literature.validation.ValidationStage;
import com.dj1012h.researchpilot.service.ChatService;
import dev.langchain4j.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String API_KEY_MARKER = "sk-test-key-must-not-appear";

    private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldLogEmptyMessageMetadataWithoutLoggingRequestValue(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(output)
                .contains("event=request_validation_failed")
                .contains("method=POST")
                .contains("path=/api/chat")
                .contains("fields=[message]")
                .contains("inputLengths=[0]")
                .contains("reasons=[NotBlank]");
    }

    @Test
    void shouldLogLongMessageLengthWithoutLoggingMessage(CapturedOutput output) throws Exception {
        String message = API_KEY_MARKER + "a".repeat(4_001);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(output)
                .contains("inputLengths=[" + message.length() + "]")
                .contains("reasons=[Size]")
                .doesNotContain(API_KEY_MARKER);
    }

    @Test
    void shouldNotLogInvalidJsonContent(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        //noinspection JsonStandardCompliance
                        .content("{\"message\":\"" + API_KEY_MARKER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(output)
                .contains("event=request_body_invalid")
                .contains("reason=INVALID_JSON")
                .doesNotContain(API_KEY_MARKER);
    }

    @Test
    void shouldNotLogExpectedModelExceptionOrExposeItsMessage(CapturedOutput output) throws Exception {
        when(chatService.chat("hello")).thenThrow(new ModelInvocationException(
                ModelFailureType.AUTHENTICATION,
                new AuthenticationException("provider echoed " + API_KEY_MARKER)
        ));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MODEL_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("模型服务认证失败"));

        assertThat(output)
                .doesNotContain(API_KEY_MARKER)
                .doesNotContain("provider echoed");
    }

    @Test
    void shouldLogUnexpectedExceptionMetadataWithoutLeakingMessageOrStack(CapturedOutput output) throws Exception {
        String sentinel = "SENSITIVE_TOKEN_8A4F secret@example.invalid Bearer secret-value jdbc:mysql://private-host/example";
        when(chatService.chat("hello")).thenThrow(new IllegalStateException(sentinel));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));

        assertThat(output)
                .contains("event=unhandled_request_error")
                .contains("stableFailureCode=INTERNAL_ERROR")
                .contains("exceptionType=java.lang.IllegalStateException")
                .doesNotContain("SENSITIVE_TOKEN_8A4F")
                .doesNotContain("secret@example.invalid")
                .doesNotContain("Bearer secret-value")
                .doesNotContain("jdbc:mysql://private-host/example");
        assertThat(countOccurrences(output.getOut(), "event=unhandled_request_error")).isOne();
    }

    @Test
    void shouldMapPersistenceFailureWithoutLeakingInfrastructureDetails(CapturedOutput output) {
        String sentinel = "jdbc:mysql://private-host/example?password=SENSITIVE_TOKEN_8A4F";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/literature/search");

        ResponseEntity<ApiErrorResponse> response = new GlobalExceptionHandler().handlePersistence(
                new LiteraturePersistenceException(sentinel, new IllegalStateException("secret@example.invalid")), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("LITERATURE_PERSISTENCE_FAILED");
        assertThat(response.getBody().details()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of("stage", "PERSISTENCE", "failureCode", "LITERATURE_PERSISTENCE_FAILED"));
        assertThat(response.getBody().toString()).doesNotContain("SENSITIVE_TOKEN_8A4F");
        assertThat(output).doesNotContain("SENSITIVE_TOKEN_8A4F").doesNotContain("secret@example.invalid");
    }

    @ParameterizedTest
    @MethodSource("modelFailureMappings")
    void shouldMapModelFailureToSafeResponse(ModelFailureType failureType,
                                              int expectedStatus,
                                              String expectedCode) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        ModelInvocationException exception = new ModelInvocationException(
                failureType,
                new RuntimeException("provider details must stay internal")
        );

        ResponseEntity<ApiErrorResponse> response =
                new GlobalExceptionHandler().handleModelInvocation(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(expectedCode);
        assertThat(response.getBody().message()).doesNotContain("provider details");
    }

    @Test
    void shouldMapSearchPlanFailureWithoutExposingValidationMessages() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/literature/search");
        SearchPlanValidationException validationException =
                new SearchPlanValidationException(
                        ValidationStage.JSON_SCHEMA,
                        List.of(new ValidationIssue(
                                "INVALID_ENUM_VALUE",
                                "$.sort",
                                "private model output must stay internal",
                                true
                        ))
                );

        ResponseEntity<ApiErrorResponse> response =
                new GlobalExceptionHandler().handleSearchPlanGeneration(
                        new SearchPlanGenerationException(validationException),
                        request
                );

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SEARCH_PLAN_GENERATION_FAILED");
        assertThat(response.getBody().details())
                .containsEntry("stage", "JSON_SCHEMA")
                .containsEntry("codes", "INVALID_ENUM_VALUE");
        assertThat(response.getBody().message()).doesNotContain("private model output");
    }

    private static Stream<Arguments> modelFailureMappings() {
        return Stream.of(
                Arguments.of(ModelFailureType.AUTHENTICATION, 502, "MODEL_AUTHENTICATION_FAILED"),
                Arguments.of(ModelFailureType.TIMEOUT, 504, "MODEL_TIMEOUT"),
                Arguments.of(ModelFailureType.UNAVAILABLE, 503, "MODEL_UNAVAILABLE"),
                Arguments.of(ModelFailureType.RATE_LIMITED, 503, "MODEL_RATE_LIMITED"),
                Arguments.of(ModelFailureType.MODEL_NOT_FOUND, 502, "MODEL_NOT_FOUND"),
                Arguments.of(ModelFailureType.INVALID_PROVIDER_REQUEST, 502, "MODEL_REQUEST_REJECTED"),
                Arguments.of(ModelFailureType.PROVIDER_ERROR, 502, "MODEL_INVOCATION_FAILED")
        );
    }

    private int countOccurrences(String text, String value) {
        int count = 0;
        int position = 0;
        while ((position = text.indexOf(value, position)) >= 0) {
            count++;
            position += value.length();
        }
        return count;
    }
}
