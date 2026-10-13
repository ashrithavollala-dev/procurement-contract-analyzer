package com.company.contractanalyzer.service;

import com.company.contractanalyzer.dto.request.UploadContractRequest;
import com.company.contractanalyzer.dto.response.ContractResponse;
import com.company.contractanalyzer.dto.response.PageResponse;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import java.nio.file.Path;
import java.util.UUID;

public interface ContractService {

    /** Validates, stores and queues the PDF, then dispatches asynchronous ingestion (HTTP 202). */
    ContractResponse upload(UploadContractRequest request);

    PageResponse<ContractResponse> list(ContractStatus status, String q, int page, int size);

    ContractResponse get(UUID id);

    StoredFile file(UUID id);

    /** FAILED -> QUEUED (or READY -> QUEUED with force); deletes existing vectors first. */
    ContractResponse reprocess(UUID id, boolean force);

    void delete(UUID id);

    /** @throws com.company.contractanalyzer.exception.ApiException 404 or 409 CONTRACT_NOT_READY */
    Contract requireReady(UUID id);

    /** Original PDF location and download name. */
    record StoredFile(Path path, String filename, long sizeBytes) {
    }
}
