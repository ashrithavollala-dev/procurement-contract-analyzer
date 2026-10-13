package com.company.contractanalyzer.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated, bounded executors for the two kinds of background work.
 *
 * <ul>
 *   <li>{@code ingestionExecutor} (core 2, max 4, queue 25, prefix "ingest-"): PDF parsing and
 *       embedding. These steps are CPU-bound, so the pool stays small on a 4-core laptop.</li>
 *   <li>{@code auditExecutor} (core 1, max 1, queue 5, prefix "audit-"): audits run one at a
 *       time, because only one LLM call may be in flight anyway.</li>
 * </ul>
 *
 * <p>Both use {@link ThreadPoolExecutor.AbortPolicy}. A full queue is rejected immediately with
 * a TaskRejectedException; callers turn it into a 503. CallerRunsPolicy was rejected because it
 * would run hours of work on a Tomcat request thread. On shutdown each pool stops accepting
 * work and waits up to its configured await-termination period for running tasks. Tasks still
 * running after that are recovered as INTERRUPTED on the next start.
 *
 * <p>The beans are {@code defaultCandidate = false}, so Spring Boot keeps its own
 * {@code applicationTaskExecutor} for Spring MVC. These pools are referenced only by name.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String INGESTION_EXECUTOR = "ingestionExecutor";
    public static final String AUDIT_EXECUTOR = "auditExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = INGESTION_EXECUTOR, defaultCandidate = false)
    public ThreadPoolTaskExecutor ingestionExecutor(AppProperties properties) {
        AppProperties.Ingestion cfg = properties.getIngestion();
        return buildExecutor("ingest-", cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity(),
                (int) cfg.getAwaitTermination().toSeconds());
    }

    @Bean(name = AUDIT_EXECUTOR, defaultCandidate = false)
    public ThreadPoolTaskExecutor auditExecutor(AppProperties properties) {
        AppProperties.Audit cfg = properties.getAudit();
        return buildExecutor("audit-", cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity(),
                (int) cfg.getAwaitTermination().toSeconds());
    }

    /** Exposes executor.* metrics (active, queued, completed, pool size) through Actuator. */
    @Bean
    public MeterBinder backgroundExecutorMetrics(
            @Qualifier(INGESTION_EXECUTOR) ThreadPoolTaskExecutor ingestionExecutor,
            @Qualifier(AUDIT_EXECUTOR) ThreadPoolTaskExecutor auditExecutor) {
        return (MeterRegistry registry) -> {
            new ExecutorServiceMetrics(ingestionExecutor.getThreadPoolExecutor(), INGESTION_EXECUTOR, Tags.empty())
                    .bindTo(registry);
            new ExecutorServiceMetrics(auditExecutor.getThreadPoolExecutor(), AUDIT_EXECUTOR, Tags.empty())
                    .bindTo(registry);
        };
    }

    static ThreadPoolTaskExecutor buildExecutor(String prefix, int core, int max, int queue, int awaitSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(Math.max(core, max));
        executor.setQueueCapacity(queue);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitSeconds);
        executor.setAllowCoreThreadTimeOut(false);
        // initialize() is invoked by Spring through afterPropertiesSet(); calling it here would leak a pool.
        return executor;
    }

    /** Last-resort logging for exceptions escaping void @Async methods: type and method only, never arguments. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("Uncaught exception in async method {}.{} (type={})",
                    method.getDeclaringClass().getSimpleName(), method.getName(), ex.getClass().getSimpleName());
            log.debug("Async failure detail", ex);
        };
    }
}
