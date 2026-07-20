package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import com.dj1012h.researchpilot.service.ChatService;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {

    private final ModelInvoker modelInvoker;

    public ChatServiceImpl(ModelInvoker modelInvoker) {
        this.modelInvoker = modelInvoker;
    }

    @Override
    public String chat(String message) {
        return modelInvoker.invoke("chat", message);
    }
}
