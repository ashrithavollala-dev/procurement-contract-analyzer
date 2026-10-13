package com.company.contractanalyzer.ai;

import java.time.Duration;

/**
 * A monotonic time budget (System.nanoTime, immune to wall-clock changes). One budget is
 * shared by every LLM call made for one rule or one question, so retries and reflection passes
 * cannot extend the total time.
 */
public final class Deadline {

    private final long deadlineNanos;

    private Deadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public static Deadline after(Duration budget) {
        return new Deadline(System.nanoTime() + budget.toNanos());
    }

    public Duration remaining() {
        long left = deadlineNanos - System.nanoTime();
        return left <= 0 ? Duration.ZERO : Duration.ofNanos(left);
    }

    public boolean isExpired() {
        return deadlineNanos - System.nanoTime() <= 0;
    }
}
