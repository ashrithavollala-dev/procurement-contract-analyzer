package com.company.contractanalyzer.dto.response;

import com.company.contractanalyzer.enums.ContractStatus;
import java.time.Instant;
import java.util.UUID;

/** Public view of a contract. storedPath and internal versioning are deliberately absent. */
public record ContractResponse(
        UUID id,
        String title,
        String vendorName,
        String originalFilename,
        String contentHash,
        long fileSizeBytes,
        Integer pageCount,
        Integer chunkCount,
        ContractStatus status,
        String errorCode,
        String errorMessage,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt) {
}
