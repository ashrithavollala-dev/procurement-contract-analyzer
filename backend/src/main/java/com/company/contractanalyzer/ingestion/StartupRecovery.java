package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.service.AuditPersistenceService;
import com.company.contractanalyzer.service.ContractLifecycleService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Recovers work that was in flight when the backend stopped (crash, kill, or a shutdown that
 * outlived the executor's await-termination period). The executors' queues live in memory, so
 * after a restart nothing will ever finish those rows. Contracts in QUEUED/PROCESSING and audit
 * runs in QUEUED/RUNNING whose last update is older than {@code app.recovery.stuck-threshold}
 * are marked FAILED with code INTERRUPTED, so the user can reprocess or re-run them.
 *
 * <p>This assumes a single backend instance. The default threshold is 0: everything in flight
 * at startup is interrupted. With several instances sharing one database, set a threshold
 * longer than the slowest job.
 */
@Component
public class StartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final ContractLifecycleService contractLifecycle;
    private final AuditPersistenceService auditPersistence;
    private final AppProperties properties;
    private final Clock clock;

    public StartupRecovery(ContractLifecycleService contractLifecycle, AuditPersistenceService auditPersistence,
                           AppProperties properties, Clock clock) {
        this.contractLifecycle = contractLifecycle;
        this.auditPersistence = auditPersistence;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recover();
    }

    /** @return number of contracts and audit runs marked INTERRUPTED */
    public RecoveryResult recover() {
        Instant cutoff = Instant.now(clock).minus(properties.getRecovery().getStuckThreshold());
        int contracts = contractLifecycle.failInterrupted(cutoff);
        int audits = auditPersistence.failInterrupted(cutoff);
        if (contracts > 0 || audits > 0) {
            log.warn("Startup recovery marked {} contract(s) and {} audit run(s) as INTERRUPTED", contracts, audits);
        } else {
            log.info("Startup recovery: no interrupted jobs");
        }
        return new RecoveryResult(contracts, audits);
    }

    public record RecoveryResult(int contracts, int audits) {
    }
}
