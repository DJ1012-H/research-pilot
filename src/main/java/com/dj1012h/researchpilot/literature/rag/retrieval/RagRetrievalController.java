package com.dj1012h.researchpilot.literature.rag.retrieval;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Diagnostics-only retrieval endpoint; the service returns a disabled result by default. */
@RestController
@RequestMapping("/api/research")
public class RagRetrievalController {

    private final RagRetrievalService retrievalService;

    public RagRetrievalController(RagRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/retrieve")
    public ResponseEntity<RagRetrievalResult> retrieve(@Valid @RequestBody RagRetrievalRequest request) {
        return ResponseEntity.ok(retrievalService.retrieve(request));
    }
}
