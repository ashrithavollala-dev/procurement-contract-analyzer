package com.company.contractanalyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.contractanalyzer.config.AppProperties;
import com.company.contractanalyzer.exception.LlmTimeoutException;
import com.company.contractanalyzer.exception.LlmUnavailableException;
import com.company.contractanalyzer.support.ControllableLlmAvailability;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class LlmGatewayTest {

    private final ControllableLlmAvailability availability = new ControllableLlmAvailability();

    @Test
    void returnsTheResultOfTheCall() {
        LlmGateway gateway = gateway(1);

        assertThat(gateway.call(Deadline.after(Duration.ofSeconds(5)), () -> "ok")).isEqualTo("ok");
        assertThat(gateway.inFlight()).isZero();
    }

    @Test
    void slowCallTimesOutAndIsInterrupted() throws Exception {
        LlmGateway gateway = gateway(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.call(Deadline.after(Duration.ofMillis(200)), () -> {
            try {
                Thread.sleep(5_000);
                return "late";
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw new IllegalStateException(e);
            }
        })).isInstanceOf(LlmTimeoutException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void semaphoreAllowsOnlyOneCallInFlight() throws Exception {
        LlmGateway gateway = gateway(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        List<CompletableFuture<String>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> gateway.call(Deadline.after(Duration.ofSeconds(10)), () -> {
                int now = concurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                concurrent.decrementAndGet();
                return "done";
            })));
        }
        for (CompletableFuture<String> call : calls) {
            assertThat(call.get(10, TimeUnit.SECONDS)).isEqualTo("done");
        }
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    void waitingForABusySlotCountsAgainstTheDeadline() throws Exception {
        LlmGateway gateway = gateway(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> holder = CompletableFuture.supplyAsync(() -> gateway.call(
                Deadline.after(Duration.ofSeconds(10)), () -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "first";
                }));
        Thread.sleep(100);

        assertThatThrownBy(() -> gateway.call(Deadline.after(Duration.ofMillis(150)), () -> "second"))
                .isInstanceOf(LlmTimeoutException.class)
                .hasMessageContaining("free");

        release.countDown();
        assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("first");
    }

    @Test
    void connectionRefusedIsUnavailableAndInvalidatesTheCachedStatus() {
        LlmGateway gateway = gateway(1);

        assertThatThrownBy(() -> gateway.call(Deadline.after(Duration.ofSeconds(5)), () -> {
            throw new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
        })).isInstanceOfSatisfying(LlmUnavailableException.class, e -> assertThat(e.isConnectionFailure()).isTrue());
        assertThat(availability.invalidations()).isEqualTo(1);
    }

    @Test
    void classifierDistinguishesTimeoutsModelErrorsAndAuth() {
        assertThat(LlmErrorClassifier.classify(new ResourceAccessException("x", new SocketTimeoutException("Read timed out"))))
                .isInstanceOf(LlmTimeoutException.class);
        assertThat(LlmErrorClassifier.classify(new RuntimeException("404 - model 'llama3.2:3b' not found")))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> assertThat(e.isConnectionFailure()).isTrue());
        assertThat(LlmErrorClassifier.classify(new RuntimeException("401 Unauthorized")))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> assertThat(e.isConnectionFailure()).isTrue());
        assertThat(LlmErrorClassifier.classify(new RuntimeException("500 Internal Server Error")))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> assertThat(e.isConnectionFailure()).isFalse());
        assertThat(LlmErrorClassifier.isToolsUnsupported(
                new RuntimeException("wrapper", new RuntimeException("registry/llama2 does not support tools")))).isTrue();
    }

    @Test
    void exhaustedDeadlineFailsImmediately() {
        LlmGateway gateway = gateway(1);

        assertThatThrownBy(() -> gateway.call(Deadline.after(Duration.ZERO), () -> "never"))
                .isInstanceOf(LlmTimeoutException.class);
    }

    private LlmGateway gateway(int permits) {
        AppProperties properties = new AppProperties();
        properties.getLlm().setMaxConcurrentCalls(permits);
        return new LlmGateway(properties, availability);
    }
}
