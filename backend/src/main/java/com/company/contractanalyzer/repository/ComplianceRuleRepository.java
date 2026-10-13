package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.entity.ComplianceRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, Long> {

    Optional<ComplianceRule> findByCode(String code);

    boolean existsByCode(String code);

    List<ComplianceRule> findAllByOrderByCodeAsc();

    List<ComplianceRule> findByActiveTrueOrderByCodeAsc();

    List<ComplianceRule> findByCodeInOrderByCodeAsc(Collection<String> codes);
}
