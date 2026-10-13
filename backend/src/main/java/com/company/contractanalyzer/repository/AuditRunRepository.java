package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.entity.AuditRun;
import com.company.contractanalyzer.enums.AuditStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRunRepository extends JpaRepository<AuditRun, UUID> {

    @EntityGraph(attributePaths = "contract")
    @Query("select a from AuditRun a where a.id = :id")
    Optional<AuditRun> findWithContractById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "contract")
    List<AuditRun> findByContractIdOrderByCreatedAtDesc(UUID contractId);

    @EntityGraph(attributePaths = "contract")
    List<AuditRun> findTop5ByOrderByCreatedAtDesc();

    List<AuditRun> findByStatusInAndUpdatedAtBefore(Collection<AuditStatus> statuses, Instant updatedBefore);

    @Query("select avg(a.riskScore) from AuditRun a where a.status = com.company.contractanalyzer.enums.AuditStatus.COMPLETED")
    Double averageCompletedRiskScore();

    /** Progress counter: a single atomic UPDATE, so concurrent readers always see a consistent value. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditRun a set a.completedRules = a.completedRules + 1, a.updatedAt = :now "
            + "where a.id = :id and a.completedRules < a.totalRules")
    int incrementCompletedRules(@Param("id") UUID id, @Param("now") Instant now);
}
