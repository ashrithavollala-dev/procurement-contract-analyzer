package com.company.contractanalyzer.dto.response;

import java.util.List;

public record SearchResponse(String query, List<SearchResultItem> results) {
}
