package com.company.contractanalyzer.entity;

import com.company.contractanalyzer.enums.FindingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The grounded result of evaluating one rule within one audit run. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_findings",
        uniqueConstraints = @UniqueConstraint(name = "uk_audit_findings_run_rule",
                columnNames = {"audit_run_id", "rule_id"}))
public class AuditFinding extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_run_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_audit_findings_run"))
    private AuditRun auditRun;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_audit_findings_rule"))
    private ComplianceRule rule;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FindingStatus status;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "evidence_quote", columnDefinition = "text")
    private String evidenceQuote;

    @Min(1)
    @Column(name = "evidence_page")
    private Integer evidencePage;

    @Column(name = "recommendation", columnDefinition = "text")
    private String recommendation;

    @Min(0)
    @Column(name = "iterations", nullable = false)
    private int iterations;

    /** JSON array of the retrieval queries used, in order (e.g. ["q1","q2"]). */
    @Column(name = "queries_used", columnDefinition = "text")
    private String queriesUsed;

    @Size(max = 40)
    @Column(name = "error_code", length = 40)
    private String errorCode;
}
