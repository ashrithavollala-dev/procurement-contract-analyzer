package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.enums.FindingStatus;
import com.company.contractanalyzer.exception.LlmTimeoutException;
import com.company.contractanalyzer.exception.LlmUnavailableException;
import com.company.contractanalyzer.ingestion.TokenEstimator;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * Evaluates one rule against one contract with a <b>bounded plan-act-observe loop</b>. The
 * orchestration is plain Java; the LLM is used only for the judgement, and that judgement
 * must be valid structured output.
 *
 * <pre>
 * for iteration in 1..maxIterations:                          (hard limit, default 2)
 *   RETRIEVE  similarity search topK, filtered by contractId; merge with earlier excerpts
 *   JUDGE     one LLM call -> RuleJudgement (structured output), one retry if invalid
 *   GROUND    GroundingValidator verifies cited ids and the verbatim quote
 *   keep the better candidate (evidence-backed beats NOT_FOUND, then higher confidence)
 *   OBSERVE   stop if a non-NOT_FOUND result has confidence >= threshold
 *   REFLECT   otherwise expand the query (deterministic synonyms) and loop
 * </pre>
 *
 * <p>Every LLM call shares the rule's {@link Deadline}. The agent never throws for LLM
 * problems. It returns NEEDS_REVIEW with LLM_TIMEOUT, LLM_UNAVAILABLE (connection failures
 * only, which the circuit breaker counts), LLM_OUTPUT_INVALID or INTERNAL_ERROR. When an
 * earlier iteration already produced a usable result, that result is kept instead. No
 * transaction is open while the agent runs.
 */
@Component
public class ComplianceAgent {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAgent.class);
    private static final String RETRY_NOTE =
            "Your previous reply was not a valid JSON object matching the required format. Reply with the JSON object only.";

    private final ChatClient chatClient;
    private final VectorChunkRepository vectorChunks;
    private final PromptLoader prompts;
    private final PromptBudget budget;
    private final QueryExpander queryExpander;
    private final GroundingValidator groundingValidator;
    private final JudgementOptionsFactory optionsFactory;
    private final LlmGateway gateway;
    private final AppProperties.Agent config;
    private final BeanOutputConverter<RuleJudgement> converter;

    public ComplianceAgent(ChatClient chatClient, VectorChunkRepository vectorChunks, PromptLoader prompts,
                           PromptBudget budget, QueryExpander queryExpander, GroundingValidator groundingValidator,
                           JudgementOptionsFactory optionsFactory, LlmGateway gateway, AppProperties properties) {
        this.chatClient = chatClient;
        this.vectorChunks = vectorChunks;
        this.prompts = prompts;
        this.budget = budget;
        this.queryExpander = queryExpander;
        this.groundingValidator = groundingValidator;
        this.optionsFactory = optionsFactory;
        this.gateway = gateway;
        this.config = properties.getAgent();
        this.converter = new BeanOutputConverter<>(RuleJudgement.class, lenientMapper());
    }

    public RuleEvaluation evaluate(UUID contractId, RuleSpec rule, Deadline deadline) {
        List<String> queries = new ArrayList<>();
        Map<String, RetrievedChunk> pool = new LinkedHashMap<>();
        GroundingValidator.Grounded best = null;
        int iterations = 0;
        String query = rule.question();

        try {
            while (iterations < config.getMaxIterations() && query != null) {
                iterations++;
                queries.add(query);

                // RETRIEVE: contract-scoped similarity search, merged with excerpts from earlier passes.
                for (RetrievedChunk chunk : vectorChunks.search(query, contractId, config.getTopK(),
                        config.getSimilarityThreshold())) {
                    pool.merge(chunk.id(), chunk, (a, b) -> a.score() >= b.score() ? a : b);
                }
                List<Excerpt> excerpts = Excerpt.number(budget.fit(new ArrayList<>(pool.values()),
                        fixedPromptTokens(rule)));

                GroundingValidator.Grounded candidate;
                if (excerpts.isEmpty()) {
                    // Nothing relevant retrieved: a deterministic NOT_FOUND, no LLM call needed.
                    candidate = new GroundingValidator.Grounded(FindingStatus.NOT_FOUND, 0.0,
                            "No passage relevant to this rule was retrieved from the contract.", null, null,
                            "Ask the vendor to add a clause covering: " + rule.title() + ".", false, null);
                } else {
                    // JUDGE (structured output, one retry), then GROUND.
                    RuleJudgement judgement = judgeWithRetry(rule, excerpts, deadline);
                    if (judgement == null) {
                        return best != null ? toEvaluation(best, iterations, queries, null)
                                : RuleEvaluation.needsReview(ErrorCode.LLM_OUTPUT_INVALID,
                                "The language model did not return valid structured output after a retry.",
                                iterations, queries);
                    }
                    candidate = groundingValidator.validate(judgement, excerpts, config.getUngroundedConfidenceCap());
                }
                best = better(best, candidate);

                // OBSERVE: good enough?
                if (best.status() != FindingStatus.NOT_FOUND && best.confidence() >= config.getConfidenceThreshold()) {
                    break;
                }
                // REFLECT: alternative query for the next pass (null when expansion is exhausted).
                query = iterations < config.getMaxIterations() ? queryExpander.expand(rule, queries, iterations) : null;
            }
            return toEvaluation(best, iterations, queries, null);
        } catch (LlmTimeoutException e) {
            log.warn("Rule {} timed out after {} iteration(s)", rule.code(), iterations);
            return best != null ? toEvaluation(best, iterations, queries, null)
                    : RuleEvaluation.needsReview(ErrorCode.LLM_TIMEOUT,
                    "The language model did not answer within the per-rule time limit.", iterations, queries);
        } catch (LlmUnavailableException e) {
            ErrorCode code = e.isConnectionFailure() ? ErrorCode.LLM_UNAVAILABLE : ErrorCode.INTERNAL_ERROR;
            log.warn("Rule {} could not be judged: code={} ({})", rule.code(), code, e.getMessage());
            if (best != null && !e.isConnectionFailure()) {
                return toEvaluation(best, iterations, queries, null);
            }
            return RuleEvaluation.needsReview(code, e.getMessage() + ".", iterations, queries);
        }
    }

    RuleJudgement judgeWithRetry(RuleSpec rule, List<Excerpt> excerpts, Deadline deadline) {
        ChatOptions options = optionsFactory.structuredOutput(converter.getJsonSchema());
        String system = prompts.template(PromptLoader.COMPLIANCE_SYSTEM);
        for (int attempt = 1; attempt <= 2; attempt++) {
            String user = renderJudgePrompt(rule, Excerpt.renderAll(excerpts), attempt == 1 ? "" : RETRY_NOTE);
            String reply = gateway.call(deadline, () -> chatClient.prompt()
                    .system(system)
                    .user(user)
                    .options(options)
                    .call()
                    .content());
            RuleJudgement judgement = parse(reply);
            String error = judgement == null ? "reply is not parseable JSON" : judgement.validationError();
            if (error == null) {
                return judgement;
            }
            log.info("Rule {} attempt {}: invalid structured output ({})", rule.code(), attempt, error);
        }
        return null;
    }

    private RuleJudgement parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        try {
            return converter.convert(reply);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String renderJudgePrompt(RuleSpec rule, String renderedExcerpts, String retryNote) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("ruleCode", rule.code());
        variables.put("ruleTitle", rule.title());
        variables.put("question", rule.question());
        variables.put("guidance", rule.guidance());
        variables.put("excerpts", renderedExcerpts);
        variables.put("retryNote", retryNote);
        variables.put("format", converter.getFormat());
        return prompts.render(PromptLoader.COMPLIANCE_JUDGE, variables);
    }

    /** Tokens of everything in the prompt except the excerpts (system, rule, format, retry note). */
    int fixedPromptTokens(RuleSpec rule) {
        return TokenEstimator.estimate(prompts.template(PromptLoader.COMPLIANCE_SYSTEM))
                + TokenEstimator.estimate(renderJudgePrompt(rule, "", RETRY_NOTE));
    }

    /** Evidence-backed statuses beat NOT_FOUND/NEEDS_REVIEW; then the higher confidence wins; ties keep the earlier. */
    static GroundingValidator.Grounded better(GroundingValidator.Grounded current, GroundingValidator.Grounded candidate) {
        if (current == null) {
            return candidate;
        }
        int rankCurrent = current.status().requiresEvidence() ? 1 : 0;
        int rankCandidate = candidate.status().requiresEvidence() ? 1 : 0;
        if (rankCandidate != rankCurrent) {
            return rankCandidate > rankCurrent ? candidate : current;
        }
        return candidate.confidence() > current.confidence() ? candidate : current;
    }

    private static RuleEvaluation toEvaluation(GroundingValidator.Grounded g, int iterations, List<String> queries,
                                               ErrorCode errorCode) {
        return new RuleEvaluation(g.status(), g.confidence(), g.explanation(), g.evidenceQuote(), g.evidencePage(),
                g.recommendation(), iterations, queries, errorCode);
    }

    private static ObjectMapper lenientMapper() {
        return JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
    }
}
