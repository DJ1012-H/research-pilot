package com.dj1012h.researchpilot.literature.api;

import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.LiteratureSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/literature")
public class LiteratureSearchController {

    private final LiteratureSearchService literatureSearchService;

    public LiteratureSearchController(LiteratureSearchService literatureSearchService) {
        this.literatureSearchService = literatureSearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @Valid @RequestBody SearchRequest request
    ) {
        return ResponseEntity.ok(literatureSearchService.search(request));
    }
}
