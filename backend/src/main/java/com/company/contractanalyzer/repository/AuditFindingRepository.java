package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.entity.AuditFinding;
import com.company.contractanalyzer.enums.FindingStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, Long> {

    @EntityGraph(attributePaths = "rule")
    List<AuditFinding> findByAuditRunId(UUID auditRunId);

    boolean existsByAuditRunIdAndRuleId(UUID auditRunId, Long ruleId);

    @Query("select new com.company.contractanalyzer.repository.RuleViolationCount(r.code, r.title, r.severity, count(f)) "
            + "from AuditFinding f join f.rule r where f.status in :statuses "
            + "group by r.code, r.title, r.severity order by count(f) desc, r.code asc")
    List<RuleViolationCount> topViolatedRules(@Param("statuses") Collection<FindingStatus> statuses, Pageable limit);
}
