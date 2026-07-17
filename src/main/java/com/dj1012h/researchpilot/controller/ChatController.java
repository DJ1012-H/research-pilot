package com.dj1012h.researchpilot.controller;

import com.dj1012h.researchpilot.dto.request.ChatRequest;
import com.dj1012h.researchpilot.dto.response.ChatResponse;
import com.dj1012h.researchpilot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(new ChatResponse(chatService.chat(request.message())));
    }
}
