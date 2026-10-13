package com.company.contractanalyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.ai.RiskScorer.ScoredFinding;
import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.enums.RiskBand;
import com.company.contractanalyzer.enums.Severity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    @ParameterizedTest(name = "{0} mandatory={1} -> factor {2}")
    @CsvSource({
        "COMPLIANT,true,0",
        "PARTIAL,true,0.5",
        "NON_COMPLIANT,true,1",
        "NEEDS_REVIEW,true,0.5",
        "NOT_FOUND,true,1",
        "NOT_FOUND,false,0.25",
        "COMPLIANT,false,0",
        "NON_COMPLIANT,false,1"
    })
    void everyFindingFactor(FindingStatus status, boolean mandatory, String factor) {
        assertThat(RiskScorer.factor(new ScoredFinding(Severity.HIGH, mandatory, status)))
                .isEqualByComparingTo(new BigDecimal(factor));
    }

    @ParameterizedTest(name = "{0} non-compliant alone scores 100")
    @CsvSource({"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    void singleNonCompliantRuleScoresHundredWhateverTheWeight(Severity severity) {
        assertThat(scorer.score(List.of(new ScoredFinding(severity, true, FindingStatus.NON_COMPLIANT))).score())
                .isEqualByComparingTo("100.0");
    }

    @Test
    void weightsAreCritical10High5Medium3Low1() {
        assertThat(Severity.CRITICAL.weight()).isEqualTo(10);
        assertThat(Severity.HIGH.weight()).isEqualTo(5);
        assertThat(Severity.MEDIUM.weight()).isEqualTo(3);
        assertThat(Severity.LOW.weight()).isEqualTo(1);
        // Weighting: a failed CRITICAL rule dominates a passed LOW rule: 100*10/11 = 90.9.
        assertThat(scorer.score(List.of(
                new ScoredFinding(Severity.CRITICAL, true, FindingStatus.NON_COMPLIANT),
                new ScoredFinding(Severity.LOW, false, FindingStatus.COMPLIANT))).score())
                .isEqualByComparingTo("90.9");
    }

    @Test
    void mixedSetIsWeightedAverageRoundedToOneDecimal() {
        // (10*0 + 5*0.5 + 3*1 + 1*0.25) / 19 * 100 = 5.75 / 19 * 100 = 30.263 -> 30.3
        RiskScorer.RiskAssessment result = scorer.score(List.of(
                new ScoredFinding(Severity.CRITICAL, true, FindingStatus.COMPLIANT),
                new ScoredFinding(Severity.HIGH, true, FindingStatus.PARTIAL),
                new ScoredFinding(Severity.MEDIUM, false, FindingStatus.NON_COMPLIANT),
                new ScoredFinding(Severity.LOW, false, FindingStatus.NOT_FOUND)));

        assertThat(result.score()).isEqualByComparingTo("30.3");
        assertThat(result.score().scale()).isEqualTo(1);
        assertThat(result.band()).isEqualTo(RiskBand.MEDIUM);
    }

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({
        "0.0,LOW", "24.9,LOW", "25.0,MEDIUM", "49.9,MEDIUM", "50.0,HIGH", "74.9,HIGH", "75.0,CRITICAL", "100.0,CRITICAL"
    })
    void bandBoundaries(String score, RiskBand band) {
        assertThat(RiskScorer.band(new BigDecimal(score))).isEqualTo(band);
    }

    @Test
    void emptySetScoresZeroLow() {
        RiskScorer.RiskAssessment result = scorer.score(List.of());

        assertThat(result.score()).isEqualByComparingTo("0.0");
        assertThat(result.band()).isEqualTo(RiskBand.LOW);
    }

    @Test
    void isDeterministic() {
        List<ScoredFinding> findings = List.of(
                new ScoredFinding(Severity.HIGH, true, FindingStatus.NEEDS_REVIEW),
                new ScoredFinding(Severity.MEDIUM, false, FindingStatus.NOT_FOUND));
        assertThat(scorer.score(findings)).isEqualTo(scorer.score(findings));
    }
}
