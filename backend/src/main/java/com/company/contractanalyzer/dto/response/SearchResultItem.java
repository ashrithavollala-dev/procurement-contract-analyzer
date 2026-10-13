package com.company.contractanalyzer.dto.response;

import java.util.UUID;

public record SearchResultItem(UUID contractId, String contractTitle, int page, int chunkIndex, String heading,
                               String snippet, double score) {
}
