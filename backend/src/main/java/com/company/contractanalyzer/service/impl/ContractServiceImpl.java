package com.company.contractanalyzer.service.impl;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.dto.request.UploadContractRequest;
import com.company.contractanalyzer.dto.response.ContractResponse;
import com.company.contractanalyzer.dto.response.PageResponse;
import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.exception.ApiException;
import com.company.contractanalyzer.exception.ResourceNotFoundException;
import com.company.contractanalyzer.ingestion.IngestionException;
import com.company.contractanalyzer.ingestion.IngestionWorker;
import com.company.contractanalyzer.ingestion.PdfInspector;
import com.company.contractanalyzer.mapper.ContractMapper;
import com.company.contractanalyzer.repository.ContractRepository;
import com.company.contractanalyzer.repository.ContractSpecifications;
import com.company.contractanalyzer.repository.VectorChunkRepository;
import com.company.contractanalyzer.service.ContractLifecycleService;
import com.company.contractanalyzer.service.ContractService;
import com.company.contractanalyzer.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contract use cases. Upload runs entirely on the request thread, with no transaction around
 * it:
 * <ol>
 *   <li>Cheap validation: emptiness, size, content type, the {@code %PDF-} magic bytes, page count.</li>
 *   <li>SHA-256 of the content and duplicate detection (409 with existingContractId).</li>
 *   <li>Insert the row as UPLOADED (its own transaction), store the file, mark it QUEUED (its own transaction).</li>
 *   <li>Only after that commit, dispatch to the {@code @Async} worker. The worker can never
 *       observe a row that is not yet committed.</li>
 * </ol>
 */
@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractServiceImpl.class);
    /** Browsers send application/pdf; generic clients often send octet-stream (then magic bytes decide). */
    private static final Set<String> ACCEPTED_CONTENT_TYPES =
            Set.of("application/pdf", "application/x-pdf", "application/octet-stream");

    private final ContractRepository contractRepository;
    private final ContractLifecycleService lifecycle;
    private final FileStorageService fileStorage;
    private final VectorChunkRepository vectorChunks;
    private final IngestionWorker ingestionWorker;
    private final PdfInspector pdfInspector;
    private final ContractMapper mapper;
    private final AppProperties properties;

    public ContractServiceImpl(ContractRepository contractRepository, ContractLifecycleService lifecycle,
                               FileStorageService fileStorage, VectorChunkRepository vectorChunks,
                               IngestionWorker ingestionWorker, PdfInspector pdfInspector, ContractMapper mapper,
                               AppProperties properties) {
        this.contractRepository = contractRepository;
        this.lifecycle = lifecycle;
        this.fileStorage = fileStorage;
        this.vectorChunks = vectorChunks;
        this.ingestionWorker = ingestionWorker;
        this.pdfInspector = pdfInspector;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public ContractResponse upload(UploadContractRequest request) {
        byte[] content = readAndValidate(request);
        String hash = sha256(content);
        contractRepository.findByContentHash(hash).ifPresent(existing -> {
            throw duplicate(existing.getId());
        });

        String filename = sanitizeFilename(request.file().getOriginalFilename());
        String title = request.title() == null || request.title().isBlank() ? titleFrom(filename) : request.title().strip();
        String vendor = request.vendorName() == null || request.vendorName().isBlank() ? null : request.vendorName().strip();
        UUID id = UUID.randomUUID();

        try {
            lifecycle.createUploaded(id, title, vendor, filename, hash, content.length);
        } catch (DataIntegrityViolationException race) {
            // Two concurrent uploads of the same file: the unique constraint decides the winner.
            throw contractRepository.findByContentHash(hash).map(c -> duplicate(c.getId())).orElseThrow(() -> race);
        }
        String storedPath;
        try {
            storedPath = fileStorage.store(id, content);
        } catch (RuntimeException e) {
            lifecycle.deleteRow(id);
            log.error("Storing upload for contract {} failed (type={})", id, e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "The file could not be stored");
        }
        Contract queued = lifecycle.markQueued(id, storedPath);
        dispatch(id);
        log.info("Contract {} uploaded and queued (bytes={})", id, content.length);
        return mapper.toResponse(queued);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> list(ContractStatus status, String q, int page, int size) {
        Specification<Contract> spec = ContractSpecifications.hasStatus(status)
                .and(ContractSpecifications.matchesText(q));
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from(contractRepository.findAll(spec, pageRequest), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse get(UUID id) {
        return mapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile file(UUID id) {
        Contract contract = find(id);
        if (contract.getStoredPath() == null) {
            throw new ResourceNotFoundException("File of contract", id);
        }
        Path path = fileStorage.resolve(contract.getStoredPath());
        try {
            return new StoredFile(path, contract.getOriginalFilename(), Files.size(path));
        } catch (IOException e) {
            throw new ResourceNotFoundException("File of contract", id);
        }
    }

    @Override
    public ContractResponse reprocess(UUID id, boolean force) {
        Contract contract = find(id);
        boolean allowed = contract.getStatus() == ContractStatus.FAILED
                || (contract.getStatus() == ContractStatus.READY && force);
        if (!allowed) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONTRACT_NOT_READY,
                    "Only FAILED contracts (or READY ones with force=true) can be reprocessed; current status is "
                            + contract.getStatus(), Map.of("status", contract.getStatus().name()));
        }
        if (contract.getStoredPath() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONTRACT_NOT_READY,
                    "The original file is missing; upload the contract again");
        }
        vectorChunks.deleteByContractId(id);
        Contract queued = lifecycle.requeueForReprocess(id);
        dispatch(id);
        return mapper.toResponse(queued);
    }

    @Override
    public void delete(UUID id) {
        Contract contract = find(id);
        if (contract.getStatus().isInFlight()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONTRACT_NOT_READY,
                    "The contract is being processed; delete it once it is READY or FAILED",
                    Map.of("status", contract.getStatus().name()));
        }
        // Order matters for retry-safety: vectors and file first (both idempotent), the row last.
        // If a step fails, the row survives and DELETE can simply be repeated.
        try {
            vectorChunks.deleteByContractId(id);
        } catch (RuntimeException e) {
            log.error("Deleting vectors of contract {} failed (type={})", id, e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "Deleting the contract's search index failed; nothing was deleted. Retry the request.");
        }
        if (contract.getStoredPath() != null) {
            try {
                fileStorage.delete(contract.getStoredPath());
            } catch (RuntimeException e) {
                log.error("Deleting the file of contract {} failed (type={})", id, e.getClass().getSimpleName());
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                        "The search index was removed but the stored file could not be deleted. Retry the request.");
            }
        }
        lifecycle.deleteRow(id);
        log.info("Contract {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Contract requireReady(UUID id) {
        Contract contract = find(id);
        if (contract.getStatus() != ContractStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONTRACT_NOT_READY,
                    "The contract must be READY; current status is " + contract.getStatus(),
                    Map.of("status", contract.getStatus().name()));
        }
        return contract;
    }

    private void dispatch(UUID id) {
        try {
            ingestionWorker.ingest(id);
        } catch (TaskRejectedException e) {
            lifecycle.markFailed(id, ErrorCode.INGESTION_QUEUE_FULL,
                    "The ingestion queue was full. Reprocess the contract to try again.");
            log.warn("Ingestion queue full; contract {} marked FAILED", id);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INGESTION_QUEUE_FULL,
                    "The ingestion queue is full; try again shortly", Map.of("contractId", id.toString()));
        }
    }

    private byte[] readAndValidate(UploadContractRequest request) {
        var file = request.file();
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("file", "must be a non-empty PDF file"));
        }
        long maxBytes = properties.getUpload().getMaxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                    "The file exceeds the maximum size of " + properties.getUpload().getMaxFileSize().toMegabytes() + " MB",
                    Map.of("maxBytes", maxBytes));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String baseType = contentType.contains(";") ? contentType.substring(0, contentType.indexOf(';')).strip() : contentType;
        if (!baseType.isEmpty() && !ACCEPTED_CONTENT_TYPES.contains(baseType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Only PDF files are supported (received " + baseType + ")");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The upload could not be read",
                    Map.of("file", "could not be read"));
        }
        if (!PdfInspector.hasPdfMagic(content)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "The file is not a PDF document");
        }
        int pages;
        try {
            pages = pdfInspector.countPages(content);
        } catch (IngestionException e) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_FILE_TYPE, e.getMessage());
        }
        int maxPages = properties.getUpload().getMaxPages();
        if (pages == 0 || pages > maxPages) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("file", "the PDF has " + pages + " pages; between 1 and " + maxPages + " are allowed"));
        }
        return content;
    }

    private Contract find(UUID id) {
        return contractRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contract", id));
    }

    private static ApiException duplicate(UUID existingId) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_CONTRACT,
                "This file has already been uploaded", Map.of("existingContractId", existingId.toString()));
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Keeps only the last path segment (browsers on Windows may send "C:\\...\\file.pdf"). */
    static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "contract.pdf";
        }
        String name = original;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty()) {
            name = "contract.pdf";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    static String titleFrom(String filename) {
        String base = filename.toLowerCase(Locale.ROOT).endsWith(".pdf") ? filename.substring(0, filename.length() - 4) : filename;
        String title = base.replace('_', ' ').replace('-', ' ').strip();
        return title.isEmpty() ? "Untitled contract" : title;
    }
}
