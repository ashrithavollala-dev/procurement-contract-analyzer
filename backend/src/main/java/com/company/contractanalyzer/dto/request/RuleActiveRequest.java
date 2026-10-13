package com.company.contractanalyzer.dto.request;

import jakarta.validation.constraints.NotNull;

public record RuleActiveRequest(@NotNull Boolean active) {
}
