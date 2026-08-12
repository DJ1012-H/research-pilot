package com.dj1012h.researchpilot.literature.rag.answer;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, bounded RAG answer endpoint. */
@RestController
@RequestMapping("/api/research")
public class RagAnswerController {
    private final RagAnswerService answerService;

    public RagAnswerController(RagAnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ResearchAnswerResponse> ask(@Valid @RequestBody ResearchQuestionRequest request) {
        return ResponseEntity.ok(answerService.answer(request));
    }
}
