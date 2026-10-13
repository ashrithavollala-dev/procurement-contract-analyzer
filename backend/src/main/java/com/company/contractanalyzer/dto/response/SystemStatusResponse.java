package com.company.contractanalyzer.dto.response;

import java.time.Instant;

public record SystemStatusResponse(
        String llmProvider,
        String llmModel,
        boolean llmReachable,
        String llmDetail,
        String qaMode,
        String embeddingModel,
        int embeddingDimension,
        Instant checkedAt) {
}
