package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.Severity;
import java.time.Instant;

public record RuleResponse(
        Long id,
        String code,
        String title,
        String question,
        String guidance,
        Severity severity,
        boolean mandatory,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
