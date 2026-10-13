package com.company.contractanalyzer.controller;

import com.company.contractanalyzer.dto.request.SemanticSearchRequest;
import com.company.contractanalyzer.dto.response.SearchResponse;
import com.company.contractanalyzer.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Semantic clause search (no LLM required)")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "Embed the query and return the most similar clauses")
    @PostMapping
    public SearchResponse search(@Valid @RequestBody SemanticSearchRequest request) {
        return searchService.search(request);
    }
}
