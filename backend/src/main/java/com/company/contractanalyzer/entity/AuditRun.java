package com.company.contractanalyzer.entity;

import com.company.contractanalyzer.enums.AuditStatus;
import com.company.contractanalyzer.enums.RiskBand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One execution of the compliance agent over a set of rules for one contract. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_runs", indexes = {
    @Index(name = "ix_audit_runs_status_updated_at", columnList = "status, updated_at")
})
public class AuditRun extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_audit_runs_contract"))
    private Contract contract;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status;

    @Min(0)
    @Column(name = "total_rules", nullable = false)
    private int totalRules;

    @Min(0)
    @Column(name = "completed_rules", nullable = false)
    private int completedRules;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Column(name = "risk_score", precision = 5, scale = 1)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_band", length = 10)
    private RiskBand riskBand;

    @Size(max = 2000)
    @Column(name = "summary", length = 2000)
    private String summary;

    @Size(max = 40)
    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AuditRun(UUID id) {
        this.id = id;
    }
}
