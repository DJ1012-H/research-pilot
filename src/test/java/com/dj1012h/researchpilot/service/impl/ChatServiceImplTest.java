package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import com.dj1012h.researchpilot.service.ChatService;
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
class ChatServiceImplTest {

    private static final String API_KEY_MARKER = "sk-test-key-must-not-appear";

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldReturnModelAnswer() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("什么是 RAG？")).thenReturn("RAG 是检索增强生成。");

        ChatService service = service();

        assertThat(service.chat("什么是 RAG？")).isEqualTo("RAG 是检索增强生成。");
    }

    @Test
    void shouldExplainWhenModelIsNotConfigured() {
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        ChatService service = service();

        assertThatThrownBy(() -> service.chat("hello"))
                .isInstanceOf(ModelNotConfiguredException.class)
                .hasMessageContaining("LLM_ENABLED=true");
    }

    @Test
    void shouldClassifyAuthenticationFailureWithoutLoggingSensitiveData(CapturedOutput output) {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello"))
                .thenThrow(new AuthenticationException("provider echoed " + API_KEY_MARKER));

        assertThatThrownBy(() -> service().chat("hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.AUTHENTICATION));

        assertThat(output)
                .contains("event=model_call_failed")
                .contains("model=test-model")
                .contains("inputLength=5")
                .contains("failureType=AUTHENTICATION")
                .doesNotContain(API_KEY_MARKER)
                .doesNotContain("provider echoed")
                .doesNotContain("hello");
    }

    @Test
    void shouldClassifyTimeoutFailure() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello")).thenThrow(new TimeoutException("timed out"));

        assertThatThrownBy(() -> service().chat("hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.TIMEOUT));
    }

    @Test
    void shouldClassifyProviderServerFailureAsUnavailable() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello")).thenThrow(new InternalServerException("provider unavailable"));

        assertThatThrownBy(() -> service().chat("hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.UNAVAILABLE));
    }

    @Test
    void shouldClassifyConnectionFailureAsUnavailable() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.chat("hello")).thenThrow(new RuntimeException(new ConnectException("connection refused")));

        assertThatThrownBy(() -> service().chat("hello"))
                .isInstanceOfSatisfying(ModelInvocationException.class, exception ->
                        assertThat(exception.getFailureType()).isEqualTo(ModelFailureType.UNAVAILABLE));
    }

    @Test
    void shouldLeaveUnexpectedRuntimeFailureForGlobalHandler() {
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        IllegalStateException unexpected = new IllegalStateException("unexpected bug");
        when(chatModel.chat("hello")).thenThrow(unexpected);

        assertThatThrownBy(() -> service().chat("hello")).isSameAs(unexpected);
    }

    private ChatService service() {
        AiProperties properties = new AiProperties();
        properties.setModelName("test-model");
        properties.setApiKey(API_KEY_MARKER);
        return new ChatServiceImpl(chatModelProvider, properties);
    }
}
