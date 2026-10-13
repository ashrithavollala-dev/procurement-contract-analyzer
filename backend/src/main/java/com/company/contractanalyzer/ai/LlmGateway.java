package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.exception.ApiException;
import com.company.contractanalyzer.exception.LlmTimeoutException;
import com.company.contractanalyzer.exception.LlmUnavailableException;
import com.company.contractanalyzer.health.LlmAvailability;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The single choke point for chat-model calls.
 *
 * <ul>
 *   <li><b>Concurrency.</b> A fair {@link Semaphore} with {@code LLM_MAX_CONCURRENT_CALLS}
 *       permits (default 1). A CPU-only laptop cannot run two 3B-model inferences usefully,
 *       and Ollama would queue them anyway.</li>
 *   <li><b>Time budget.</b> Waiting for a permit and the call itself share one
 *       {@link Deadline}. When it expires the caller gets {@link LlmTimeoutException} and the
 *       call is cancelled (interrupted).</li>
 *   <li><b>Honest accounting.</b> The permit is released in the call's own {@code finally}, not
 *       when the caller gives up, so "at most N calls in flight" stays true even after a
 *       timeout.</li>
 *   <li><b>Error mapping.</b> Failures go through {@link LlmErrorClassifier}. Connection
 *       failures also invalidate the cached status so the UI banner updates.</li>
 * </ul>
 * No database connection or transaction is ever open on the threads that call this.
 */
@Component
public class LlmGateway {

    private final int maxPermits;
    private final Semaphore permits;
    private final ExecutorService callExecutor;
    private final LlmAvailability availability;

    public LlmGateway(AppProperties properties, LlmAvailability availability) {
        int max = properties.getLlm().getMaxConcurrentCalls();
        this.maxPermits = max;
        this.permits = new Semaphore(max, true);
        this.availability = availability;
        AtomicInteger counter = new AtomicInteger();
        // At most `max` tasks exist at once (each holds a permit), so a pool of `max` never queues.
        this.callExecutor = new ThreadPoolExecutor(max, max, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(max),
                runnable -> {
                    Thread thread = new Thread(runnable, "llm-call-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public <T> T call(Deadline deadline, Supplier<T> action) {
        Duration waitBudget = deadline.remaining();
        if (waitBudget.isZero()) {
            throw new LlmTimeoutException("The time budget for this request is exhausted");
        }
        try {
            if (!permits.tryAcquire(waitBudget.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new LlmTimeoutException("Timed out waiting for the language model to become free");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmTimeoutException("Interrupted while waiting for the language model");
        }
        Future<T> future;
        try {
            future = callExecutor.submit(() -> {
                try {
                    return action.get();
                } finally {
                    permits.release();
                }
            });
        } catch (RejectedExecutionException e) {
            permits.release();
            throw new LlmUnavailableException("The language model call could not be scheduled", false);
        }
        try {
            return future.get(Math.max(1, deadline.remaining().toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new LlmTimeoutException("The language model did not answer within the time budget");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new LlmTimeoutException("Interrupted while waiting for the language model");
        } catch (ExecutionException e) {
            ApiException mapped = LlmErrorClassifier.classify(e.getCause());
            if (mapped instanceof LlmUnavailableException unavailable && unavailable.isConnectionFailure()) {
                availability.invalidate();
            }
            throw mapped;
        }
    }

    /** Number of calls currently holding a permit (in flight). */
    public int inFlight() {
        return maxPermits - permits.availablePermits();
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }
}
