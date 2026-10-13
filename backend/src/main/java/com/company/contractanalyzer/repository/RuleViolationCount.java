package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.enums.Severity;

/** JPQL constructor projection: how often a rule was found non-compliant or missing. */
public record RuleViolationCount(String ruleCode, String ruleTitle, Severity severity, long count) {
}
