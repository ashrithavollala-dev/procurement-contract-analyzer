package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.entity.Contract;
import com.company.contractanalyzer.enums.ContractStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {

    Optional<Contract> findByContentHash(String contentHash);

    List<Contract> findByStatusInAndUpdatedAtBefore(Collection<ContractStatus> statuses, Instant updatedBefore);

    @Query("select new com.company.contractanalyzer.repository.StatusCount(c.status, count(c)) "
            + "from Contract c group by c.status")
    List<StatusCount> countGroupedByStatus();

    /**
     * Atomic compare-and-set state transition (no read-modify-write race): only moves the row
     * if it is still in {@code from}. Returns the number of rows changed (0 or 1).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Contract c set c.status = :to, c.updatedAt = :now, c.version = c.version + 1 "
            + "where c.id = :id and c.status = :from")
    int transition(@Param("id") UUID id, @Param("from") ContractStatus from,
                   @Param("to") ContractStatus to, @Param("now") Instant now);
}
