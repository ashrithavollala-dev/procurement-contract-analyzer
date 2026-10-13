package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.config.AsyncConfig;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The asynchronous entry point, in its own bean so that calls from ContractService pass through
 * the Spring proxy. Self-invocation of an {@code @Async} method would bypass the proxy and run
 * synchronously on the HTTP thread.
 *
 * <p>Runs on the bounded {@code ingestionExecutor}. When its queue is full, the proxy throws
 * {@link org.springframework.core.task.TaskRejectedException} synchronously to the caller,
 * which marks the contract FAILED/INGESTION_QUEUE_FULL and answers 503.
 */
@Component
public class IngestionWorker {

    private final IngestionPipeline pipeline;

    public IngestionWorker(IngestionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Async(AsyncConfig.INGESTION_EXECUTOR)
    public void ingest(UUID contractId) {
        pipeline.process(contractId);
    }
}
