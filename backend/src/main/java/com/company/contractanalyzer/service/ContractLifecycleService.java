package com.company.contractanalyzer.service;

import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import com.company.contractanalyzer.enums.ErrorCode;
import com.company.contractanalyzer.repository.ContractRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every contract state change, each in its own short transaction (REQUIRES_NEW), so a status is
 * committed and visible to polling clients immediately. The pipeline calls these methods
 * between the slow steps (PDF parsing, embedding) and never holds a transaction across them.
 */
@Service
@RequiredArgsConstructor
public class ContractLifecycleService {

    public static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final ContractRepository contractRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Contract createUploaded(UUID id, String title, String vendorName, String originalFilename,
                                   String contentHash, long fileSizeBytes) {
        Contract contract = new Contract(id);
        contract.setTitle(title);
        contract.setVendorName(vendorName);
        contract.setOriginalFilename(originalFilename);
        contract.setContentHash(contentHash);
        contract.setFileSizeBytes(fileSizeBytes);
        contract.setStatus(ContractStatus.UPLOADED);
        return contractRepository.saveAndFlush(contract);
    }

    /** UPLOADED -> QUEUED once the file is safely stored. Commits before the async job is dispatched. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Contract markQueued(UUID id, String storedPath) {
        Contract contract = contractRepository.findById(id).orElseThrow();
        contract.setStoredPath(storedPath);
        contract.setStatus(ContractStatus.QUEUED);
        return contractRepository.saveAndFlush(contract);
    }

    /** FAILED/READY -> QUEUED for reprocessing; clears previous results and errors. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Contract requeueForReprocess(UUID id) {
        Contract contract = contractRepository.findById(id).orElseThrow();
        contract.setStatus(ContractStatus.QUEUED);
        contract.setErrorCode(null);
        contract.setErrorMessage(null);
        contract.setChunkCount(null);
        contract.setPageCount(null);
        contract.setProcessedAt(null);
        return contractRepository.saveAndFlush(contract);
    }

    /** Atomic QUEUED -> PROCESSING. Returns false if the contract was deleted or already taken. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessing(UUID id) {
        return contractRepository.transition(id, ContractStatus.QUEUED, ContractStatus.PROCESSING, now()) == 1;
    }

    /** PROCESSING -> READY. Returns false if the contract disappeared meanwhile (deleted). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markReady(UUID id, int pageCount, int chunkCount) {
        Optional<Contract> found = contractRepository.findById(id);
        if (found.isEmpty() || found.get().getStatus() != ContractStatus.PROCESSING) {
            return false;
        }
        Contract contract = found.get();
        contract.setStatus(ContractStatus.READY);
        contract.setPageCount(pageCount);
        contract.setChunkCount(chunkCount);
        contract.setProcessedAt(now());
        contract.setErrorCode(null);
        contract.setErrorMessage(null);
        contractRepository.saveAndFlush(contract);
        return true;
    }

    /** Any state -> FAILED with a sanitised message. Returns false if the contract no longer exists. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(UUID id, ErrorCode code, String message) {
        Optional<Contract> found = contractRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        Contract contract = found.get();
        contract.setStatus(ContractStatus.FAILED);
        contract.setErrorCode(code.name());
        contract.setErrorMessage(sanitize(message));
        contractRepository.saveAndFlush(contract);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteRow(UUID id) {
        contractRepository.deleteById(id);
        contractRepository.flush();
    }

    /** Startup recovery: in-flight contracts older than the threshold are marked FAILED/INTERRUPTED. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failInterrupted(Instant updatedBefore) {
        List<Contract> stuck = contractRepository.findByStatusInAndUpdatedAtBefore(
                List.of(ContractStatus.QUEUED, ContractStatus.PROCESSING), updatedBefore);
        for (Contract contract : stuck) {
            contract.setStatus(ContractStatus.FAILED);
            contract.setErrorCode(ErrorCode.INTERRUPTED.name());
            contract.setErrorMessage("Processing was interrupted by a backend restart. Reprocess the contract to retry.");
        }
        contractRepository.saveAllAndFlush(stuck);
        return stuck.size();
    }

    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Processing failed";
        }
        String singleLine = message.replaceAll("[\\r\\n\\t]+", " ").strip();
        return singleLine.length() > MAX_ERROR_MESSAGE_LENGTH
                ? singleLine.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "..."
                : singleLine;
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
