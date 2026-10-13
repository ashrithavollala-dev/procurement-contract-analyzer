package com.company.contractanalyzer.service;

import com.company.contractanalyzer.dto.request.SemanticSearchRequest;
import com.company.contractanalyzer.dto.response.SearchResponse;

/** Semantic clause search. Embeddings are local, so this works without any LLM. */
public interface SearchService {

    SearchResponse search(SemanticSearchRequest request);
}
