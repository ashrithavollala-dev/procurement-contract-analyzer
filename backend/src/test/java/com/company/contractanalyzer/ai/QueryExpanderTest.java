package com.company.contractanalyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.enums.Severity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryExpanderTest {

    private final QueryExpander expander = new QueryExpander();

    @Test
    void seededRuleExpandsToContractVocabularyAndNeverRepeatsAQuery() {
        RuleSpec rule = new RuleSpec(1L, "LIABILITY_CAP", "Limitation of liability cap", "Is liability capped?",
                "Guidance", Severity.CRITICAL, true);
        List<String> previous = new ArrayList<>(List.of(rule.question()));

        String first = expander.expand(rule, previous, 1);
        previous.add(first);
        String second = expander.expand(rule, previous, 2);

        assertThat(first).contains("liability").isNotEqualTo(rule.question());
        assertThat(second).isNotIn(previous.subList(0, 2));
    }

    @Test
    void customRuleFallsBackToTitleAndContentWordsThenStops() {
        RuleSpec rule = new RuleSpec(99L, "WARRANTY_PERIOD", "Warranty period",
                "Does the contract define a warranty period for the delivered goods?", "Guidance", Severity.MEDIUM, false);
        List<String> previous = new ArrayList<>(List.of(rule.question()));

        String expansion = expander.expand(rule, previous, 1);
        previous.add(expansion);

        assertThat(expansion).startsWith("Warranty period").contains("warranty", "period", "delivered", "goods")
                .doesNotContain(" the ");
        assertThat(expander.expand(rule, previous, 2)).isNull();
    }

    @Test
    void isDeterministic() {
        RuleSpec rule = new RuleSpec(1L, "AUDIT_RIGHTS", "Audit rights", "Q?", "G", Severity.HIGH, true);
        assertThat(expander.expand(rule, List.of("Q?"), 1)).isEqualTo(expander.expand(rule, List.of("Q?"), 1));
    }
}
