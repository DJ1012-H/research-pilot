package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import com.dj1012h.researchpilot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ModelInvoker modelInvoker;

    @Test
    void shouldDelegateChatToSharedModelInvoker() {
        when(modelInvoker.invoke("chat", "什么是 RAG？")).thenReturn("RAG 是检索增强生成。");

        ChatService service = service();

        assertThat(service.chat("什么是 RAG？")).isEqualTo("RAG 是检索增强生成。");
        verify(modelInvoker).invoke("chat", "什么是 RAG？");
    }

    private ChatService service() {
        return new ChatServiceImpl(modelInvoker);
    }
}
