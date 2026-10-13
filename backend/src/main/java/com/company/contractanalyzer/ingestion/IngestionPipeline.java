package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.service.ContractLifecycleService;
import com.company.contractanalyzer.service.FileStorageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One ingestion job: QUEUED -> PROCESSING -> extract -> chunk -> embed + store -> READY.
 *
 * <p>Deliberately <b>not</b> transactional. Each status change is a separate short transaction in
 * {@link ContractLifecycleService}. PDF parsing and embedding run with no database connection
 * held, so a slow document cannot exhaust the HikariCP pool. On any failure the contract's
 * vectors are deleted and the contract is marked FAILED with a code and a sanitised message.
 */
@Component
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final ContractRepository contractRepository;
    private final ContractLifecycleService lifecycle;
    private final FileStorageService fileStorage;
    private final PdfTextExtractor extractor;
    private final ClauseAwareChunker chunker;
    private final VectorChunkRepository vectorChunks;
    private final AppProperties properties;

    public IngestionPipeline(ContractRepository contractRepository, ContractLifecycleService lifecycle,
                             FileStorageService fileStorage, PdfTextExtractor extractor,
                             ClauseAwareChunker chunker, VectorChunkRepository vectorChunks,
                             AppProperties properties) {
        this.contractRepository = contractRepository;
        this.lifecycle = lifecycle;
        this.fileStorage = fileStorage;
        this.extractor = extractor;
        this.chunker = chunker;
        this.vectorChunks = vectorChunks;
        this.properties = properties;
    }

    public void process(UUID contractId) {
        if (!lifecycle.markProcessing(contractId)) {
            log.info("Skipping ingestion of contract {}: no longer QUEUED (deleted or already taken)", contractId);
            return;
        }
        long started = System.nanoTime();
        try {
            Optional<Contract> contract = contractRepository.findById(contractId);
            if (contract.isEmpty() || contract.get().getStoredPath() == null) {
                throw new IngestionException(ErrorCode.INTERNAL_ERROR, "Stored file reference is missing");
            }
            byte[] pdf = fileStorage.read(contract.get().getStoredPath());

            List<PageText> pages = extractor.extract(pdf);
            int characters = PdfTextExtractor.extractableCharacters(pages);
            if (characters < properties.getIngestion().getMinTextChars()) {
                throw new IngestionException(ErrorCode.NO_EXTRACTABLE_TEXT,
                        "The PDF contains no extractable text (" + characters + " characters). "
                                + "Scanned documents need OCR, which is not supported.");
            }

            List<Chunk> chunks = chunker.chunk(pages.stream().map(PageText::text).toList());
            // Idempotent re-runs: remove anything left by a previous attempt, then upsert by deterministic id.
            vectorChunks.deleteByContractId(contractId);
            vectorChunks.addChunks(contractId, chunks, properties.getIngestion().getEmbeddingBatchSize());

            if (!lifecycle.markReady(contractId, pages.size(), chunks.size())) {
                log.info("Contract {} was removed during ingestion; cleaning up its vectors", contractId);
                vectorChunks.deleteByContractId(contractId);
                return;
            }
            log.info("Contract {} ingested: pages={} chunks={} durationMs={}", contractId, pages.size(),
                    chunks.size(), (System.nanoTime() - started) / 1_000_000);
        } catch (IngestionException e) {
            fail(contractId, e.getCode(), e.getMessage(), e);
        } catch (RuntimeException e) {
            fail(contractId, ErrorCode.INTERNAL_ERROR,
                    "Unexpected ingestion error (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private void fail(UUID contractId, ErrorCode code, String message, Exception cause) {
        // Stack traces only at DEBUG; the WARN line carries the code and exception type, never contract text.
        log.warn("Ingestion of contract {} failed: code={} type={}", contractId, code, cause.getClass().getSimpleName());
        log.debug("Ingestion failure detail for contract {}", contractId, cause);
        try {
            vectorChunks.deleteByContractId(contractId);
        } catch (RuntimeException cleanupFailure) {
            log.error("Vector cleanup failed for contract {} (type={})", contractId,
                    cleanupFailure.getClass().getSimpleName());
        }
        lifecycle.markFailed(contractId, code, message);
    }
}
