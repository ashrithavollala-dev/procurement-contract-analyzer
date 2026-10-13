package com.company.contractanalyzer.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.company.contractanalyzer.ai.ComplianceAgent;
import com.company.contractanalyzer.ai.Deadline;
import com.company.contractanalyzer.ai.RuleEvaluation;
import com.company.contractanalyzer.ai.RuleSpec;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import com.company.contractanalyzer.enums.Severity;
import com.company.contractanalyzer.health.LlmStatusService;
import com.company.contractanalyzer.ingestion.IngestionWorker;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.RetrievedChunk;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.service.ContractLifecycleService;
import com.company.contractanalyzer.service.FileStorageService;
import com.company.contractanalyzer.support.SamplePdfGenerator;
import com.company.contractanalyzer.support.TestcontainersConfig;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * OPTIONAL real-model smoke test. It is excluded from {@code mvn verify} and CI and runs only
 * with {@code mvn -Plive-it verify}. It needs Docker, internet access or a warm embedding cache
 * for the ~80 MB ONNX model download, and optionally a running Ollama with llama3.2:3b. It uses
 * the real model configuration (Ollama chat plus Transformers embeddings, no test doubles) and
 * proves:
 * <ol>
 *   <li>exactly one EmbeddingModel (Transformers) is active, emitting 384-dimensional vectors;</li>
 *   <li>ingestion plus PgVectorStore similarity search find the liability clause with real embeddings;</li>
 *   <li>if Ollama is reachable, one real rule judgement completes (any status). Otherwise this
 *       part is skipped, not failed.</li>
 * </ol>
 */
@Tag("live")
@SpringBootTest(properties = {
    "spring.ai.model.chat=ollama",
    "spring.ai.model.embedding=transformers",
    "spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}",
    "app.llm.ollama-base-url=${OLLAMA_BASE_URL:http://localhost:11434}",
    "app.llm.status-cache-ttl=0s",
    "app.llm.probe-timeout=3s",
    "app.agent.rule-timeout=300s"
})
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class LiveModelsSmokeTest {

    @Autowired private ApplicationContext context;
    @Autowired private EmbeddingModel embeddingModel;
    @Autowired private ContractLifecycleService lifecycle;
    @Autowired private FileStorageService storage;
    @Autowired private IngestionWorker worker;
    @Autowired private ContractRepository contracts;
    @Autowired private VectorChunkRepository vectors;
    @Autowired private LlmStatusService llmStatus;
    @Autowired private ComplianceAgent agent;

    @Test
    void realModelsIngestSearchAndOptionallyJudge() throws Exception {
        assertThat(context.getBeansOfType(EmbeddingModel.class)).hasSize(1);
        assertThat(embeddingModel).isInstanceOf(TransformersEmbeddingModel.class);
        assertThat(embeddingModel.dimensions()).isEqualTo(384);
        assertThat(context.getBeansOfType(ChatModel.class).values()).singleElement().isInstanceOf(OllamaChatModel.class);

        byte[] pdf = SamplePdfGenerator.wellFormedContract();
        UUID id = UUID.randomUUID();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        lifecycle.createUploaded(id, "Live smoke " + id, "Fictional", "contract-wellformed.pdf", hash, pdf.length);
        lifecycle.markQueued(id, storage.store(id, pdf));
        worker.ingest(id);
        await().atMost(Duration.ofMinutes(5)).until(() ->
                contracts.findById(id).map(Contract::getStatus).orElseThrow() != ContractStatus.QUEUED
                        && contracts.findById(id).map(Contract::getStatus).orElseThrow() != ContractStatus.PROCESSING);
        assertThat(contracts.findById(id).orElseThrow().getStatus()).isEqualTo(ContractStatus.READY);

        List<RetrievedChunk> hits = vectors.search("limitation of liability cap on aggregate liability", id, 3, 0.0);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).text().toLowerCase()).contains("liability");

        Assumptions.assumeTrue(llmStatus.status().reachable(), "Ollama not reachable: skipping the real judgement");
        RuleSpec rule = new RuleSpec(3L, "LIABILITY_CAP", "Limitation of liability cap",
                "Does the contract cap the supplier's total aggregate liability?",
                "Compliant: an explicit aggregate cap with carve-outs for fraud and data breaches.", Severity.CRITICAL, true);
        RuleEvaluation evaluation = agent.evaluate(id, rule, Deadline.after(Duration.ofMinutes(5)));
        assertThat(evaluation.status()).isNotNull();
        assertThat(evaluation.iterations()).isBetween(1, 2);
        System.out.println("LIVE judgement: status=" + evaluation.status() + " confidence=" + evaluation.confidence()
                + " page=" + evaluation.evidencePage() + " errorCode=" + evaluation.errorCode());
    }
}
