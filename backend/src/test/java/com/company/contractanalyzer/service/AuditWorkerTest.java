package com.company.contractanalyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.contractanalyzer.ai.AuditSummaryBuilder;
import com.company.contractanalyzer.ai.ComplianceAgent;
import com.company.contractanalyzer.ai.RiskScorer;
import com.company.contractanalyzer.ai.RuleEvaluation;
import com.company.contractanalyzer.ai.RuleSpec;
import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.enums.RiskBand;
import com.company.contractanalyzer.enums.Severity;
import com.company.contractanalyzer.support.ControllableLlmAvailability;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Per-rule isolation, the deterministic score, and the circuit breaker, with the agent mocked. */
class AuditWorkerTest {

    private final UUID runId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();
    private final ComplianceAgent agent = mock(ComplianceAgent.class);
    private final AuditPersistenceService persistence = mock(AuditPersistenceService.class);
    private final ControllableLlmAvailability availability = new ControllableLlmAvailability();
    private AuditWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AuditWorker(agent, persistence, new RiskScorer(), new AuditSummaryBuilder(), availability,
                new AppProperties());
        when(persistence.markRunning(runId)).thenReturn(true);
        when(persistence.saveFinding(eq(runId), anyLong(), any())).thenReturn(true);
    }

    @Test
    void failingRulesBecomeNeedsReviewAndTheRunContinues() {
        List<RuleSpec> rules = rules(3, Severity.HIGH);
        when(agent.evaluate(eq(contractId), eq(rules.get(0)), any()))
                .thenReturn(RuleEvaluation.needsReview(ErrorCode.LLM_TIMEOUT, "timed out", 1, List.of("q")));
        when(agent.evaluate(eq(contractId), eq(rules.get(1)), any())).thenThrow(new IllegalStateException("boom"));
        when(agent.evaluate(eq(contractId), eq(rules.get(2)), any())).thenReturn(compliant());

        worker.execute(runId, contractId, rules);

        verify(persistence).saveFinding(eq(runId), eq(1L), argThat(e -> e.errorCode() == ErrorCode.LLM_TIMEOUT));
        verify(persistence).saveFinding(eq(runId), eq(2L), argThat(e -> e.errorCode() == ErrorCode.INTERNAL_ERROR
                && e.status() == FindingStatus.NEEDS_REVIEW));
        verify(persistence).saveFinding(eq(runId), eq(3L), argThat(e -> e.status() == FindingStatus.COMPLIANT));
        // HIGH weights: (0.5 + 0.5 + 0) * 5 / 15 * 100 = 33.3 -> MEDIUM
        verify(persistence).complete(eq(runId), argThat(score -> score.compareTo(new BigDecimal("33.3")) == 0),
                eq(RiskBand.MEDIUM), argThat(summary -> summary.startsWith("3 rules evaluated: 1 compliant")));
        verify(persistence, never()).fail(any(), any(), any());
    }

    @Test
    void threeConsecutiveUnreachableFailuresAbortTheRun() {
        List<RuleSpec> rules = rules(5, Severity.MEDIUM);
        when(agent.evaluate(eq(contractId), any(), any()))
                .thenReturn(RuleEvaluation.needsReview(ErrorCode.LLM_UNAVAILABLE, "refused", 1, List.of("q")));

        worker.execute(runId, contractId, rules);

        verify(agent, times(3)).evaluate(eq(contractId), any(), any());
        verify(persistence, times(3)).saveFinding(eq(runId), anyLong(), any());
        verify(persistence).fail(eq(runId), eq(ErrorCode.LLM_UNAVAILABLE), argThat(s -> s.contains("3 of 5")));
        verify(persistence, never()).complete(any(), any(), any(), any());
        assertThat(availability.invalidations()).isEqualTo(1);
    }

    @Test
    void timeoutsAndSuccessesResetTheUnreachableStreak() {
        List<RuleSpec> rules = rules(6, Severity.LOW);
        List<RuleEvaluation> outcomes = new ArrayList<>(List.of(
                unreachable(), unreachable(),
                RuleEvaluation.needsReview(ErrorCode.LLM_TIMEOUT, "slow", 1, List.of()),
                unreachable(), unreachable(), compliant()));
        when(agent.evaluate(eq(contractId), any(), any())).thenAnswer(invocation -> outcomes.remove(0));

        worker.execute(runId, contractId, rules);

        verify(persistence, times(6)).saveFinding(eq(runId), anyLong(), any());
        verify(persistence).complete(eq(runId), any(), any(), any());
        verify(persistence, never()).fail(any(), any(), any());
    }

    @Test
    void runThatIsNoLongerQueuedIsSkipped() {
        when(persistence.markRunning(runId)).thenReturn(false);

        worker.execute(runId, contractId, rules(2, Severity.HIGH));

        verify(agent, never()).evaluate(any(), any(), any());
    }

    @Test
    void runDeletedMidwayStopsQuietly() {
        List<RuleSpec> rules = rules(3, Severity.HIGH);
        when(agent.evaluate(eq(contractId), any(), any())).thenReturn(compliant());
        when(persistence.saveFinding(eq(runId), eq(1L), any())).thenReturn(false);

        worker.execute(runId, contractId, rules);

        verify(agent, times(1)).evaluate(any(), any(), any());
        verify(persistence, never()).complete(any(), any(), any(), any());
    }

    private static RuleEvaluation compliant() {
        return new RuleEvaluation(FindingStatus.COMPLIANT, 0.9, "ok", "quote text here", 2, "none", 1, List.of("q"), null);
    }

    private static RuleEvaluation unreachable() {
        return RuleEvaluation.needsReview(ErrorCode.LLM_UNAVAILABLE, "refused", 1, List.of());
    }

    private static List<RuleSpec> rules(int count, Severity severity) {
        List<RuleSpec> rules = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            rules.add(new RuleSpec(i, "RULE_" + i, "Rule " + i, "Question " + i + "?", "Guidance", severity, true));
        }
        return rules;
    }
}
