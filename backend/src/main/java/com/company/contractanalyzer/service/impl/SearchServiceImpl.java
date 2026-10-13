package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.dto.request.SemanticSearchRequest;
import com.company.contractanalyzer.dto.response.SearchResponse;
import com.company.contractanalyzer.dto.response.SearchResultItem;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.exception.ResourceNotFoundException;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.service.SearchService;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private final VectorChunkRepository vectorChunks;
    private final ContractRepository contractRepository;
    private final AppProperties properties;

    public SearchServiceImpl(VectorChunkRepository vectorChunks, ContractRepository contractRepository,
                             AppProperties properties) {
        this.vectorChunks = vectorChunks;
        this.contractRepository = contractRepository;
        this.properties = properties;
    }

    @Override
    public SearchResponse search(SemanticSearchRequest request) {
        if (request.contractId() != null && !contractRepository.existsById(request.contractId())) {
            throw new ResourceNotFoundException("Contract", request.contractId());
        }
        int topK = request.topK() == null ? properties.getSearch().getDefaultTopK() : request.topK();
        double minScore = request.minScore() == null ? properties.getSearch().getDefaultMinScore() : request.minScore();
        String query = request.query().strip();

        // Embedding + vector search run without a transaction; titles are fetched in one IN query afterwards.
        List<RetrievedChunk> hits = vectorChunks.search(query, request.contractId(), topK, minScore);
        Set<UUID> contractIds = new LinkedHashSet<>();
        hits.forEach(hit -> contractIds.add(hit.contractId()));
        Map<UUID, String> titles = new HashMap<>();
        for (Contract contract : contractRepository.findAllById(contractIds)) {
            titles.put(contract.getId(), contract.getTitle());
        }
        int snippetLength = properties.getSearch().getSnippetLength();
        List<SearchResultItem> results = hits.stream()
                // A vector whose contract row is gone (deleted mid-request) is skipped.
                .filter(hit -> titles.containsKey(hit.contractId()))
                .map(hit -> new SearchResultItem(hit.contractId(), titles.get(hit.contractId()), hit.page(),
                        hit.chunkIndex(), hit.heading(), snippet(hit.text(), snippetLength), round(hit.score())))
                .toList();
        return new SearchResponse(query, results);
    }

    public static String snippet(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.length() <= maxLength) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', maxLength - 1);
        return flat.substring(0, cut > maxLength / 2 ? cut : maxLength - 1) + "…";
    }

    public static double round(double score) {
        return Math.round(score * 10_000.0) / 10_000.0;
    }
}
