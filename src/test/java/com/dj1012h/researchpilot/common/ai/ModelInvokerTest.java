package com.dj1012h.researchpilot.common.ai;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ModelInvokerTest {

    private static final String API_KEY_MARKER = "sk-test-key-must-not-appear";

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnModelAnswerAndLogOnlySafePerformanceFields(CapturedOutput output) {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("prompt")).thenReturn("answer");

        assertThat(invoker().invoke("search_plan", "prompt")).isEqualTo("answer");
        assertThat(output)
                .contains("event=model_call_succeeded")
                .contains("operation=search_plan")
                .contains("model=test-model")
                .contains("inputLength=6")
                .containsPattern("durationMs=\\d+")
                .doesNotContain(API_KEY_MARKER)
                .doesNotContain("prompt")
                .doesNotContain("answer");
    }

    @Test
    void shouldExplainWhenModelIsNotConfigured() {
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> invoker().invoke("chat", "hello"))
                .isInstanceOf(ModelNotConfiguredException.class)
                .hasMessageContaining("LLM_ENABLED=true");
    }

    @Test
    void shouldClassifyAuthenticationFailureWithoutLoggingSensitiveData(CapturedOutput output) {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("prompt"))
                .thenThrow(new AuthenticationException("provider echoed " + API_KEY_MARKER));

        assertThatThrownBy(() -> invoker().invoke("search_plan", "prompt"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.AUTHENTICATION));

        assertThat(output)
                .contains("event=model_call_failed")
                .contains("operation=search_plan")
                .contains("model=test-model")
                .contains("inputLength=6")
                .contains("failureType=AUTHENTICATION")
                .doesNotContain(API_KEY_MARKER)
                .doesNotContain("provider echoed")
                .doesNotContain("prompt");
    }

    @Test
    void shouldClassifyTimeoutFailure() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello")).thenThrow(new TimeoutException("timed out"));

        assertThatThrownBy(() -> invoker().invoke("chat", "hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.TIMEOUT));
    }

    @Test
    void shouldClassifyProviderServerFailureAsUnavailable() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello")).thenThrow(new InternalServerException("provider unavailable"));

        assertThatThrownBy(() -> invoker().invoke("chat", "hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.UNAVAILABLE));
    }

    @Test
    void shouldClassifyConnectionFailureAsUnavailable() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello")).thenThrow(new RuntimeException(new ConnectException("connection refused")));

        assertThatThrownBy(() -> invoker().invoke("chat", "hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.UNAVAILABLE));
    }

    @Test
    void shouldLeaveUnexpectedRuntimeFailureForGlobalHandler() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        IllegalStateException unexpected = new IllegalStateException("unexpected bug");
        when(chatModel.chat("hello")).thenThrow(unexpected);

        assertThatThrownBy(() -> invoker().invoke("chat", "hello")).isSameAs(unexpected);
    }

    private ModelInvoker invoker() {
        AiProperties properties = new AiProperties();
        properties.setModelName("test-model");
        properties.setApiKey(API_KEY_MARKER);
        return new ModelInvoker(chatModelProvider, properties);
    }
}
