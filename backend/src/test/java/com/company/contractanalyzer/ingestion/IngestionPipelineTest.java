package com.company.contractanalyzer.ingestion;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Failure handling of the pipeline with every collaborator mocked. */
class IngestionPipelineTest {

    private final UUID id = UUID.randomUUID();
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final ContractLifecycleService lifecycle = mock(ContractLifecycleService.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final PdfTextExtractor extractor = mock(PdfTextExtractor.class);
    private final VectorChunkRepository vectors = mock(VectorChunkRepository.class);
    private IngestionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new IngestionPipeline(contracts, lifecycle, storage, extractor,
                new ClauseAwareChunker(ChunkingOptions.DEFAULTS), vectors, new AppProperties());
        Contract contract = new Contract(id);
        contract.setStoredPath(id + ".pdf");
        when(contracts.findById(id)).thenReturn(Optional.of(contract));
        when(storage.read(anyString())).thenReturn(new byte[] {1});
        when(lifecycle.markProcessing(id)).thenReturn(true);
        when(extractor.extract(new byte[] {1})).thenReturn(List.of(new PageText(1,
                "ARTICLE 1 SCOPE\n1.1 " + "The Supplier provides fictional logistics services to the Customer. ".repeat(10))));
    }

    @Test
    void embeddingFailureCleansUpVectorsAndMarksFailed() {
        doThrow(new IllegalStateException("embedding exploded")).when(vectors).addChunks(eq(id), anyList(), anyInt());

        pipeline.process(id);

        InOrder order = inOrder(vectors, lifecycle);
        order.verify(vectors).deleteByContractId(id);
        order.verify(vectors).addChunks(eq(id), anyList(), eq(32));
        order.verify(vectors).deleteByContractId(id);
        order.verify(lifecycle).markFailed(eq(id), eq(ErrorCode.INTERNAL_ERROR), contains("IllegalStateException"));
        verify(lifecycle, never()).markReady(eq(id), anyInt(), anyInt());
    }

    @Test
    void lowTextIsNoExtractableTextAndNothingIsEmbedded() {
        when(extractor.extract(new byte[] {1})).thenReturn(List.of(new PageText(1, "x"), new PageText(2, "")));

        pipeline.process(id);

        verify(vectors, never()).addChunks(eq(id), anyList(), anyInt());
        verify(lifecycle).markFailed(eq(id), eq(ErrorCode.NO_EXTRACTABLE_TEXT), contains("no extractable text"));
    }

    @Test
    void contractDeletedDuringProcessingHasItsVectorsRemoved() {
        when(lifecycle.markReady(eq(id), anyInt(), anyInt())).thenReturn(false);

        pipeline.process(id);

        verify(vectors, times(2)).deleteByContractId(id);
        verify(lifecycle, never()).markFailed(eq(id), eq(ErrorCode.INTERNAL_ERROR), anyString());
    }

    @Test
    void contractNoLongerQueuedIsSkipped() {
        when(lifecycle.markProcessing(id)).thenReturn(false);

        pipeline.process(id);

        verifyNoInteractions(storage, extractor, vectors);
    }
}
