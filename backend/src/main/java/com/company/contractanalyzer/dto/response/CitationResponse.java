package com.company.contractanalyzer.dto.response;

public record CitationResponse(int page, int chunkIndex, String snippet, double score) {
}
