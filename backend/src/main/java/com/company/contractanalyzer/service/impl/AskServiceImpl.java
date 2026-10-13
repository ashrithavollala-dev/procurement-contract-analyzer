package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.ai.ContractQaAgent;
import com.company.contractanalyzer.ai.QaAnswer;
import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.dto.request.AskRequest;
import com.company.contractanalyzer.dto.response.AskResponse;
import com.company.contractanalyzer.dto.response.CitationResponse;
import com.company.contractanalyzer.exception.LlmUnavailableException;
import com.company.contractanalyzer.health.LlmAvailability;
import com.company.contractanalyzer.health.LlmStatus;
import com.company.contractanalyzer.service.AskService;
import com.company.contractanalyzer.service.ContractService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Not transactional: the LLM call may take minutes and must not hold a database connection. */
@Service
public class AskServiceImpl implements AskService {

    private static final Logger log = LoggerFactory.getLogger(AskServiceImpl.class);

    private final ContractService contractService;
    private final ContractQaAgent qaAgent;
    private final LlmAvailability llmAvailability;
    private final AppProperties properties;

    public AskServiceImpl(ContractService contractService, ContractQaAgent qaAgent, LlmAvailability llmAvailability,
                          AppProperties properties) {
        this.contractService = contractService;
        this.qaAgent = qaAgent;
        this.llmAvailability = llmAvailability;
        this.properties = properties;
    }

    @Override
    public AskResponse ask(UUID contractId, AskRequest request) {
        contractService.requireReady(contractId);
        LlmStatus status = llmAvailability.status();
        if (!status.reachable()) {
            throw new LlmUnavailableException("The language model is not available: " + status.detail());
        }
        long started = System.nanoTime();
        QaAnswer answer = qaAgent.answer(contractId, request.question().strip());
        log.info("Question answered for contract {}: mode={} grounded={} citations={} durationMs={}", contractId,
                answer.mode().value(), answer.grounded(), answer.citations().size(),
                (System.nanoTime() - started) / 1_000_000);
        int snippetLength = properties.getSearch().getSnippetLength();
        return new AskResponse(answer.answer(), answer.grounded(), answer.mode().value(),
                answer.citations().stream()
                        .map(c -> new CitationResponse(c.page(), c.chunkIndex(),
                                SearchServiceImpl.snippet(c.text(), snippetLength), SearchServiceImpl.round(c.score())))
                        .toList());
    }
}
