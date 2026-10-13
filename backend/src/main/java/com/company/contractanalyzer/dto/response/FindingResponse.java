package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.enums.Severity;
import java.math.BigDecimal;
import java.util.List;

public record FindingResponse(
        Long id,
        String ruleCode,
        String ruleTitle,
        Severity severity,
        boolean mandatory,
        FindingStatus status,
        BigDecimal confidence,
        String explanation,
        String evidenceQuote,
        Integer evidencePage,
        String recommendation,
        int iterations,
        List<String> queriesUsed,
        String errorCode) {
}
