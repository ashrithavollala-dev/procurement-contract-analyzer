package com.company.contractanalyzer.entity;

import com.company.contractanalyzer.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A configurable procurement policy rule evaluated by the compliance agent. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "compliance_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_compliance_rules_code", columnNames = "code"),
        indexes = @Index(name = "ix_compliance_rules_active", columnList = "active"))
public class ComplianceRule extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$")
    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotBlank
    @Size(max = 2000)
    @Column(name = "question", nullable = false, length = 2000)
    private String question;

    @NotBlank
    @Size(max = 4000)
    @Column(name = "guidance", nullable = false, length = 4000)
    private String guidance;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
