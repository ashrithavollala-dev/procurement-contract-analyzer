package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.enums.QaMode;
import com.company.contractanalyzer.ingestion.TokenEstimator;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Grounded Q&A about one contract, in one of two modes ({@code QA_MODE}).
 *
 * <ul>
 *   <li><b>rag</b> (default): Spring AI's {@link RetrievalAugmentationAdvisor} with the steps
 *       below. Small local models call tools unreliably, which is why this is the default.
 *     <ul>
 *       <li>A {@link VectorStoreDocumentRetriever} scoped to the contract through the
 *           {@code FILTER_EXPRESSION} advisor parameter.</li>
 *       <li>A {@link PromptBudget} post-processor that drops the lowest-scoring excerpts to
 *           fit {@code num_ctx}.</li>
 *       <li>A {@link ContextualQueryAugmenter} that wraps each excerpt in delimiters.</li>
 *     </ul>
 *     The retriever's default filter matches nothing, so a missing parameter can never
 *     search across contracts.</li>
 *   <li><b>tools</b>: the documented user-controlled tool loop: {@link ToolCallingManager} with
 *       {@code internalToolExecutionEnabled=false}, at most {@code QA_MAX_TOOL_ROUNDS} rounds,
 *       and read-only {@link ContractTools} scoped by the server-side ToolContext. If the
 *       model rejects tools, the agent falls back to rag once and logs it.</li>
 * </ul>
 * Citations always come from chunks the server retrieved. With none, {@code grounded=false}
 * and the answer is the fixed "cannot find" sentence, regardless of what the model wrote.
 */
@Component
public class ContractQaAgent {

    public static final String NOT_FOUND_ANSWER = "I cannot find this in the contract.";
    static final String ROUND_LIMIT_ANSWER = "I could not finish looking this up within the allowed number of steps.";

    private static final Logger log = LoggerFactory.getLogger(ContractQaAgent.class);
    private static final Filter.Expression MATCH_NOTHING =
            VectorChunkRepository.contractFilter(new UUID(0L, 0L));

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final ContractTools contractTools;
    private final PromptLoader prompts;
    private final PromptBudget budget;
    private final LlmGateway gateway;
    private final AppProperties.Qa config;
    private final RetrievalAugmentationAdvisor ragAdvisor;

    public ContractQaAgent(ChatClient chatClient, ChatModel chatModel, ToolCallingManager toolCallingManager,
                           ContractTools contractTools, VectorStore vectorStore, PromptLoader prompts,
                           PromptBudget budget, LlmGateway gateway, AppProperties properties) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.contractTools = contractTools;
        this.prompts = prompts;
        this.budget = budget;
        this.gateway = gateway;
        this.config = properties.getQa();
        this.ragAdvisor = buildRagAdvisor(vectorStore);
    }

    public QaAnswer answer(UUID contractId, String question) {
        Deadline deadline = Deadline.after(config.getTimeout());
        if (config.getMode() == QaMode.TOOLS) {
            try {
                return answerWithTools(contractId, question, deadline);
            } catch (ToolsUnsupportedException e) {
                log.warn("QA_MODE=tools but the model rejected tool definitions; falling back to rag mode for this request");
            }
        }
        return answerWithRag(contractId, question, deadline);
    }

    // ------------------------------------------------------------------ rag mode

    QaAnswer answerWithRag(UUID contractId, String question, Deadline deadline) {
        String system = prompts.template(PromptLoader.QA_SYSTEM);
        ChatClientResponse response = gateway.call(deadline, () -> chatClient.prompt()
                .system(system)
                .user(question)
                .advisors(ragAdvisor)
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        VectorChunkRepository.contractFilter(contractId)))
                .call()
                .chatClientResponse());
        List<RetrievedChunk> citations = new ArrayList<>();
        Object documents = response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (documents instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Document document) {
                    citations.add(VectorChunkRepository.toRetrievedChunk(document));
                }
            }
        }
        return finish(text(response.chatResponse()), citations, QaMode.RAG);
    }

    private RetrievalAugmentationAdvisor buildRagAdvisor(VectorStore vectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(config.getTopK())
                        .similarityThreshold(config.getSimilarityThreshold())
                        .filterExpression(() -> MATCH_NOTHING)
                        .build())
                .documentPostProcessors((query, documents) -> fitToBudget(query.text(), documents))
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(new PromptTemplate(prompts.template(PromptLoader.QA_RAG_CONTEXT)))
                        .emptyContextPromptTemplate(new PromptTemplate(prompts.template(PromptLoader.QA_EMPTY_CONTEXT)))
                        .allowEmptyContext(false)
                        .documentFormatter(documents -> Excerpt.renderAll(Excerpt.number(
                                documents.stream().map(VectorChunkRepository::toRetrievedChunk).toList())))
                        .build())
                .taskExecutor(new SyncTaskExecutor())
                .build();
    }

    /** Drops the lowest-scoring documents until system prompt + template + question + excerpts fit num_ctx. */
    List<Document> fitToBudget(String question, List<Document> documents) {
        int fixed = TokenEstimator.estimate(prompts.template(PromptLoader.QA_SYSTEM))
                + TokenEstimator.estimate(prompts.template(PromptLoader.QA_RAG_CONTEXT))
                + TokenEstimator.estimate(question);
        Map<String, Document> byId = new HashMap<>();
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (Document document : documents) {
            byId.put(document.getId(), document);
            chunks.add(VectorChunkRepository.toRetrievedChunk(document));
        }
        List<Document> kept = new ArrayList<>();
        for (RetrievedChunk chunk : budget.fit(chunks, fixed)) {
            kept.add(byId.get(chunk.id()));
        }
        return kept;
    }

    // ------------------------------------------------------------------ tools mode

    QaAnswer answerWithTools(UUID contractId, String question, Deadline deadline) {
        CitationCollector collector = new CitationCollector();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ContractTools.CTX_CONTRACT_ID, contractId);
        toolContext.put(ContractTools.CTX_CITATIONS, collector);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(contractTools))
                .toolContext(toolContext)
                .internalToolExecutionEnabled(false)
                .build();
        List<Message> messages = List.of(
                new SystemMessage(prompts.template(PromptLoader.QA_TOOLS_SYSTEM)),
                new UserMessage(question));

        String answer = gateway.call(deadline, () -> {
            Prompt prompt = new Prompt(messages, options);
            ChatResponse response = callRejectingUnsupportedTools(prompt);
            int rounds = 0;
            while (response.hasToolCalls() && rounds < config.getMaxToolRounds()) {
                ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
                prompt = new Prompt(result.conversationHistory(), options);
                response = chatModel.call(prompt);
                rounds++;
            }
            return response.hasToolCalls() ? ROUND_LIMIT_ANSWER : text(response);
        });
        return finish(answer, collector.chunks(), QaMode.TOOLS);
    }

    private ChatResponse callRejectingUnsupportedTools(Prompt prompt) {
        try {
            return chatModel.call(prompt);
        } catch (RuntimeException e) {
            if (LlmErrorClassifier.isToolsUnsupported(e)) {
                throw new ToolsUnsupportedException();
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ shared

    private static QaAnswer finish(String modelAnswer, List<RetrievedChunk> citations, QaMode mode) {
        if (citations.isEmpty()) {
            return new QaAnswer(NOT_FOUND_ANSWER, false, mode, List.of());
        }
        String answer = modelAnswer == null || modelAnswer.isBlank() ? NOT_FOUND_ANSWER : modelAnswer.strip();
        List<RetrievedChunk> sorted = new ArrayList<>(citations);
        sorted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return new QaAnswer(answer, true, mode, sorted);
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}
