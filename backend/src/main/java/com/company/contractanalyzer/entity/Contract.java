package com.company.contractanalyzer.entity;

import com.company.contractanalyzer.enums.ContractStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An uploaded procurement contract and its ingestion state. Never exposed directly by the API. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contracts",
        uniqueConstraints = @UniqueConstraint(name = "uk_contracts_content_hash", columnNames = "content_hash"),
        indexes = {
            @Index(name = "ix_contracts_status", columnList = "status"),
            @Index(name = "ix_contracts_status_updated_at", columnList = "status, updated_at")
        })
public class Contract extends AuditableEntity {

    /** Assigned by the application before the file is stored, so the file name is known up front. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Size(max = 255)
    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @NotNull
    @Pattern(regexp = "^[0-9a-f]{64}$")
    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Min(0)
    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Min(0)
    @Column(name = "page_count")
    private Integer pageCount;

    @Min(0)
    @Column(name = "chunk_count")
    private Integer chunkCount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    @Size(max = 40)
    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** File name relative to UPLOAD_DIR ({id}.pdf); never exposed through the API. */
    @Size(max = 500)
    @Column(name = "stored_path", length = 500)
    private String storedPath;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Contract(UUID id) {
        this.id = id;
    }
}
