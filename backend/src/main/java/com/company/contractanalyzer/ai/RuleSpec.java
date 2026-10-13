package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.Severity;

/**
 * Immutable snapshot of a compliance rule, handed to the async audit worker. A detached JPA
 * entity is never passed across threads.
 */
public record RuleSpec(Long id, String code, String title, String question, String guidance,
                       Severity severity, boolean mandatory) {
}
