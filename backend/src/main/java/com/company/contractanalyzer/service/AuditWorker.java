package com.company.contractanalyzer.service;

import com.company.contractanalyzer.ai.AuditSummaryBuilder;
import com.company.contractanalyzer.ai.ComplianceAgent;
import com.company.contractanalyzer.ai.Deadline;
import com.company.contractanalyzer.ai.RiskScorer;
import com.company.contractanalyzer.ai.RuleEvaluation;
import com.company.contractanalyzer.ai.RuleSpec;
import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.config.AsyncConfig;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.health.LlmAvailability;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs one audit on the bounded {@code auditExecutor}. It is a separate bean so that the
 * {@code @Async} proxy applies to calls from AuditService.
 *
 * <p>Rules are evaluated one after another with per-rule isolation. Each rule gets its own
 * time budget ({@code AGENT_RULE_TIMEOUT_SECONDS}) and its own try/catch, so a failing rule
 * becomes a NEEDS_REVIEW finding and the run continues. A circuit breaker aborts the run as
 * FAILED/LLM_UNAVAILABLE after {@code AGENT_MAX_CONSECUTIVE_LLM_FAILURES} (default 3)
 * consecutive connection failures. Timeouts do not count, because a slow model is still
 * reachable. A successfully judged rule resets the counter. Each finding is persisted in its
 * own short transaction as soon as it is ready, so the UI can show progress.
 */
@Component
public class AuditWorker {

    private static final Logger log = LoggerFactory.getLogger(AuditWorker.class);

    private final ComplianceAgent agent;
    private final AuditPersistenceService persistence;
    private final RiskScorer riskScorer;
    private final AuditSummaryBuilder summaryBuilder;
    private final LlmAvailability llmAvailability;
    private final AppProperties.Agent config;

    public AuditWorker(ComplianceAgent agent, AuditPersistenceService persistence, RiskScorer riskScorer,
                       AuditSummaryBuilder summaryBuilder, LlmAvailability llmAvailability, AppProperties properties) {
        this.agent = agent;
        this.persistence = persistence;
        this.riskScorer = riskScorer;
        this.summaryBuilder = summaryBuilder;
        this.llmAvailability = llmAvailability;
        this.config = properties.getAgent();
    }

    @Async(AsyncConfig.AUDIT_EXECUTOR)
    public void run(UUID runId, UUID contractId, List<RuleSpec> rules) {
        execute(runId, contractId, rules);
    }

    /** Synchronous body (also used directly by tests). */
    public void execute(UUID runId, UUID contractId, List<RuleSpec> rules) {
        if (!persistence.markRunning(runId)) {
            log.info("Audit {} is no longer queued (deleted or recovered); skipping", runId);
            return;
        }
        long started = System.nanoTime();
        List<RiskScorer.ScoredFinding> scored = new ArrayList<>();
        List<AuditSummaryBuilder.SummaryItem> items = new ArrayList<>();
        int consecutiveUnreachable = 0;
        try {
            for (RuleSpec rule : rules) {
                RuleEvaluation evaluation = evaluateIsolated(contractId, rule);
                if (!persistence.saveFinding(runId, rule.id(), evaluation)) {
                    log.info("Audit {} was deleted while running; stopping", runId);
                    return;
                }
                scored.add(new RiskScorer.ScoredFinding(rule.severity(), rule.mandatory(), evaluation.status()));
                items.add(new AuditSummaryBuilder.SummaryItem(rule.code(), rule.severity(), evaluation.status()));
                log.info("Audit {} rule {} -> {} (confidence={}, iterations={}, errorCode={})", runId, rule.code(),
                        evaluation.status(), evaluation.confidence(), evaluation.iterations(), evaluation.errorCode());

                if (evaluation.errorCode() == ErrorCode.LLM_UNAVAILABLE) {
                    consecutiveUnreachable++;
                } else if (evaluation.errorCode() == null) {
                    consecutiveUnreachable = 0;
                }
                if (consecutiveUnreachable >= config.getMaxConsecutiveLlmFailures()) {
                    llmAvailability.invalidate();
                    persistence.fail(runId, ErrorCode.LLM_UNAVAILABLE,
                            summaryBuilder.aborted(scored.size(), rules.size(), consecutiveUnreachable));
                    log.warn("Audit {} aborted: LLM unreachable for {} consecutive rules", runId, consecutiveUnreachable);
                    return;
                }
            }
            RiskScorer.RiskAssessment risk = riskScorer.score(scored);
            persistence.complete(runId, risk.score(), risk.band(), summaryBuilder.completed(items, risk));
            log.info("Audit {} completed: rules={} score={} band={} durationMs={}", runId, rules.size(),
                    risk.score(), risk.band(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.error("Audit {} failed unexpectedly (type={})", runId, e.getClass().getSimpleName());
            log.debug("Audit failure detail", e);
            persistence.fail(runId, ErrorCode.INTERNAL_ERROR, summaryBuilder.failed(scored.size(), rules.size()));
        }
    }

    /** One rule, fully isolated: whatever happens, a finding comes back. */
    private RuleEvaluation evaluateIsolated(UUID contractId, RuleSpec rule) {
        try {
            return agent.evaluate(contractId, rule, Deadline.after(config.getRuleTimeout()));
        } catch (RuntimeException e) {
            log.warn("Rule {} failed unexpectedly (type={})", rule.code(), e.getClass().getSimpleName());
            return RuleEvaluation.needsReview(ErrorCode.INTERNAL_ERROR,
                    "The rule could not be evaluated because of an internal error.", 0, List.of());
        }
    }
}
