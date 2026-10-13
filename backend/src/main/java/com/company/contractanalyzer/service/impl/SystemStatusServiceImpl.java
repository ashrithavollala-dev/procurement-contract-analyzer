package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.dto.response.SystemStatusResponse;
import com.company.contractanalyzer.health.EmbeddingHealthIndicator;
import com.company.contractanalyzer.health.LlmAvailability;
import com.company.contractanalyzer.health.LlmStatus;
import com.company.contractanalyzer.service.SystemStatusService;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {

    private final LlmAvailability llmAvailability;
    private final AppProperties properties;

    public SystemStatusServiceImpl(LlmAvailability llmAvailability, AppProperties properties) {
        this.llmAvailability = llmAvailability;
        this.properties = properties;
    }

    @Override
    public SystemStatusResponse status() {
        LlmStatus llm = llmAvailability.status();
        return new SystemStatusResponse(llm.provider(), llm.model(), llm.reachable(), llm.detail(),
                properties.getQa().getMode().value(), EmbeddingHealthIndicator.MODEL_NAME,
                properties.getVector().getDimensions(), llm.checkedAt());
    }
}
