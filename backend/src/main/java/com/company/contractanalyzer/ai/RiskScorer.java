package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.enums.RiskBand;
import com.company.contractanalyzer.enums.Severity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic risk score. The LLM never computes it.
 *
 * <pre>
 * weight:  CRITICAL=10, HIGH=5, MEDIUM=3, LOW=1
 * factor:  COMPLIANT=0, PARTIAL=0.5, NON_COMPLIANT=1, NEEDS_REVIEW=0.5,
 *          NOT_FOUND=1 (mandatory rule) or 0.25 (optional rule)
 * score  = round(100 * sum(weight * factor) / sum(weight), 1)   over the evaluated rules
 * band   = LOW below 25, MEDIUM below 50, HIGH below 75, CRITICAL from 75
 * </pre>
 * An empty set scores 0.0 (LOW). The arithmetic uses BigDecimal, so 33.35 rounds HALF_UP the
 * same way on every JVM.
 */
@Component
public class RiskScorer {

    /** Input for one evaluated rule. */
    public record ScoredFinding(Severity severity, boolean mandatory, FindingStatus status) {
    }

    public record RiskAssessment(BigDecimal score, RiskBand band) {
    }

    public RiskAssessment score(List<ScoredFinding> findings) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ScoredFinding finding : findings) {
            BigDecimal weight = BigDecimal.valueOf(finding.severity().weight());
            weighted = weighted.add(weight.multiply(factor(finding)));
            totalWeight = totalWeight.add(weight);
        }
        BigDecimal score = totalWeight.signum() == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : weighted.multiply(BigDecimal.valueOf(100)).divide(totalWeight, 1, RoundingMode.HALF_UP);
        return new RiskAssessment(score, band(score));
    }

    static BigDecimal factor(ScoredFinding finding) {
        return switch (finding.status()) {
            case COMPLIANT -> BigDecimal.ZERO;
            case PARTIAL, NEEDS_REVIEW -> new BigDecimal("0.5");
            case NON_COMPLIANT -> BigDecimal.ONE;
            case NOT_FOUND -> finding.mandatory() ? BigDecimal.ONE : new BigDecimal("0.25");
        };
    }

    public static RiskBand band(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(25)) < 0) {
            return RiskBand.LOW;
        }
        if (score.compareTo(BigDecimal.valueOf(50)) < 0) {
            return RiskBand.MEDIUM;
        }
        if (score.compareTo(BigDecimal.valueOf(75)) < 0) {
            return RiskBand.HIGH;
        }
        return RiskBand.CRITICAL;
    }
}
