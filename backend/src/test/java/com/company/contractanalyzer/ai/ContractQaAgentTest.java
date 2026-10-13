package com.company.contractanalyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.enums.QaMode;
import com.company.contractanalyzer.repository.AuditFindingRepository;
import com.company.contractanalyzer.repository.AuditRunRepository;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.support.ControllableLlmAvailability;
import com.company.contractanalyzer.support.StubChatModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class ContractQaAgentTest {

    private static final UUID CONTRACT = UUID.randomUUID();
    private static final UUID OTHER_CONTRACT = UUID.randomUUID();

    private final StubChatModel stub = new StubChatModel();
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final VectorChunkRepository vectorChunks = mock(VectorChunkRepository.class);
    private final AppProperties properties = new AppProperties();

    @Test
    void ragAnswerIsGroundedWithServerSideCitations() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("d1", 2, 7, "10.3 The Customer may terminate this Agreement for convenience on sixty (60) days written notice.", 0.71),
                document("d2", 4, 15, "11.2 If a force majeure event continues for more than sixty days either party may terminate.", 0.42)));
        stub.thenReply("Sixty days' written notice (page 2).");

        QaAnswer answer = agent().answer(CONTRACT, "What is the notice period for termination for convenience?");

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.mode()).isEqualTo(QaMode.RAG);
        assertThat(answer.answer()).isEqualTo("Sixty days' written notice (page 2).");
        assertThat(answer.citations()).extracting(RetrievedChunk::page).containsExactly(2, 4);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getFilterExpression()).isEqualTo(VectorChunkRepository.contractFilter(CONTRACT));
        String user = ComplianceAgentTest.text(stub.prompts().get(0), MessageType.USER);
        assertThat(user).contains("<<<EXCERPT E1 | page 2>>>").contains("QUESTION: What is the notice period");
        assertThat(ComplianceAgentTest.text(stub.prompts().get(0), MessageType.SYSTEM)).contains("untrusted DATA");
    }

    @Test
    void nothingRetrievedMeansNotGroundedWhateverTheModelSays() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        stub.thenReply("Probably thirty days.");

        QaAnswer answer = agent().answer(CONTRACT, "What colour is the logo?");

        assertThat(answer.grounded()).isFalse();
        assertThat(answer.citations()).isEmpty();
        assertThat(answer.answer()).isEqualTo(ContractQaAgent.NOT_FOUND_ANSWER);
    }

    @Test
    void ragExcerptsAreBudgetedToTheContextWindow() {
        // About 5300 estimated tokens: larger than the whole 4096-token window on its own.
        String longText = "word ".repeat(4000);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("big", 1, 1, longText, 0.9), document("small", 2, 2, "Short clause about notices.", 0.5)));

        List<Document> kept = agent().fitToBudget("question?", vectorStore.similaritySearch(SearchRequest.builder().query("x").build()));

        // The higher-scoring excerpt alone exceeds num_ctx, so nothing lower-scored may replace it.
        assertThat(kept).extracting(Document::getId).isEmpty();
    }

    @Test
    void toolsModeUsesTheServerSideContractIdEvenIfTheModelSendsAnother() {
        properties.getQa().setMode(QaMode.TOOLS);
        RetrievedChunk clause = new RetrievedChunk("c1", CONTRACT, 2, 7,
                "ARTICLE 10", "10.3 The Customer may terminate for convenience on sixty (60) days written notice.", 0.7);
        when(vectorChunks.search(anyString(), eq(CONTRACT), anyInt(), anyDouble())).thenReturn(List.of(clause));
        stub.thenToolCall("searchClauses",
                        "{\"query\":\"termination for convenience notice\",\"topK\":3,\"contractId\":\"" + OTHER_CONTRACT + "\"}")
                .thenReply("Sixty days (page 2).");

        QaAnswer answer = agent().answer(CONTRACT, "How much notice is needed to terminate for convenience?");

        verify(vectorChunks).search(eq("termination for convenience notice"), eq(CONTRACT), eq(3), anyDouble());
        verify(vectorChunks, never()).search(anyString(), eq(OTHER_CONTRACT), anyInt(), anyDouble());
        assertThat(answer.mode()).isEqualTo(QaMode.TOOLS);
        assertThat(answer.grounded()).isTrue();
        assertThat(answer.citations()).extracting(RetrievedChunk::id).containsExactly("c1");
        assertThat(answer.answer()).isEqualTo("Sixty days (page 2).");
        // The second model call received the tool result, delimited as untrusted data.
        assertThat(stub.prompts().get(1).getInstructions())
                .anySatisfy(m -> assertThat(m.getMessageType()).isEqualTo(MessageType.TOOL));
    }

    @Test
    void toolRoundsAreBounded() {
        properties.getQa().setMode(QaMode.TOOLS);
        properties.getQa().setMaxToolRounds(2);
        when(vectorChunks.search(anyString(), eq(CONTRACT), anyInt(), anyDouble())).thenReturn(List.of(
                new RetrievedChunk("c1", CONTRACT, 1, 1, null, "Some clause text for the test.", 0.6)));
        for (int i = 0; i < 5; i++) {
            stub.thenToolCall("searchClauses", "{\"query\":\"again " + i + "\"}");
        }

        QaAnswer answer = agent().answer(CONTRACT, "Loop forever?");

        assertThat(stub.callCount()).isEqualTo(3);
        assertThat(answer.answer()).isEqualTo(ContractQaAgent.ROUND_LIMIT_ANSWER);
    }

    @Test
    void toolsModeFallsBackToRagWhenTheModelRejectsTools() {
        properties.getQa().setMode(QaMode.TOOLS);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                document("d1", 3, 9, "12.1 This Agreement is governed by the laws of the fictional State of Westmarch.", 0.8)));
        stub.thenThrow(new RuntimeException("400 - registry.ollama.ai/library/tiny does not support tools"))
                .thenReply("The laws of Westmarch (page 3).");

        QaAnswer answer = agent().answer(CONTRACT, "Which law governs the contract?");

        assertThat(answer.mode()).isEqualTo(QaMode.RAG);
        assertThat(answer.grounded()).isTrue();
        assertThat(answer.citations()).extracting(RetrievedChunk::page).containsExactly(3);
    }

    private ContractQaAgent agent() {
        ContractTools tools = new ContractTools(vectorChunks, mock(ContractRepository.class), mock(AuditRunRepository.class),
                mock(AuditFindingRepository.class), properties);
        return new ContractQaAgent(ChatClient.create(stub), stub, ToolCallingManager.builder().build(), tools, vectorStore,
                new PromptLoader(), new PromptBudget(4096, 700),
                new LlmGateway(properties, new ControllableLlmAvailability()), properties);
    }

    private static Document document(String id, int page, int chunkIndex, String text, double score) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(VectorChunkRepository.META_CONTRACT_ID, CONTRACT.toString());
        metadata.put(VectorChunkRepository.META_PAGE, page);
        metadata.put(VectorChunkRepository.META_CHUNK_INDEX, chunkIndex);
        return Document.builder().id(id).text(text).metadata(metadata).score(score).build();
    }
}
