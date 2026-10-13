package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.Severity;

public record RuleViolationStat(String ruleCode, String ruleTitle, Severity severity, long count) {
}
