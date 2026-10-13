package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.enums.Severity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the audit run summary from counts only: deterministic text, no LLM involved. */
@Component
public class AuditSummaryBuilder {

    /** One evaluated rule as seen by the summary. */
    public record SummaryItem(String ruleCode, Severity severity, FindingStatus status) {
    }

    public String completed(List<SummaryItem> items, RiskScorer.RiskAssessment risk) {
        Map<FindingStatus, Integer> counts = count(items);
        StringBuilder sb = new StringBuilder();
        sb.append(items.size()).append(items.size() == 1 ? " rule evaluated: " : " rules evaluated: ")
                .append(counts.get(FindingStatus.COMPLIANT)).append(" compliant, ")
                .append(counts.get(FindingStatus.PARTIAL)).append(" partial, ")
                .append(counts.get(FindingStatus.NON_COMPLIANT)).append(" non-compliant, ")
                .append(counts.get(FindingStatus.NOT_FOUND)).append(" not found, ")
                .append(counts.get(FindingStatus.NEEDS_REVIEW)).append(" need review. ")
                .append("Risk score ").append(risk.score().toPlainString()).append(" (").append(risk.band()).append(").");
        List<String> gaps = new ArrayList<>();
        for (SummaryItem item : items) {
            boolean gap = item.status() == FindingStatus.NON_COMPLIANT || item.status() == FindingStatus.NOT_FOUND;
            if (gap && (item.severity() == Severity.CRITICAL || item.severity() == Severity.HIGH)) {
                gaps.add(item.ruleCode());
            }
        }
        if (!gaps.isEmpty()) {
            sb.append(" High-severity gaps: ").append(String.join(", ", gaps)).append('.');
        }
        return sb.toString();
    }

    public String aborted(int evaluated, int total, int consecutiveFailures) {
        return "Audit aborted after " + evaluated + " of " + total + " rules: the language model was unreachable for "
                + consecutiveFailures + " consecutive rules. Check the LLM provider and start a new audit.";
    }

    public String failed(int evaluated, int total) {
        return "Audit failed unexpectedly after " + evaluated + " of " + total + " rules.";
    }

    private static Map<FindingStatus, Integer> count(List<SummaryItem> items) {
        Map<FindingStatus, Integer> counts = new EnumMap<>(FindingStatus.class);
        for (FindingStatus status : FindingStatus.values()) {
            counts.put(status, 0);
        }
        for (SummaryItem item : items) {
            counts.merge(item.status(), 1, Integer::sum);
        }
        return counts;
    }
}
