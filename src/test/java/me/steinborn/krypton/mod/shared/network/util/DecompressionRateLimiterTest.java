package me.steinborn.krypton.mod.shared.network.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecompressionRateLimiterTest {
    private static final long WINDOW_NANOS = 1_000_000_000L;

    @Test
    void allowsRequestsUpToTheConfiguredBudget() {
        AtomicLong now = new AtomicLong(10L);
        DecompressionRateLimiter limiter = newLimiter(1024, now);

        assertDoesNotThrow(() -> limiter.consume(512));
        assertDoesNotThrow(() -> limiter.consume(512));
    }

    @Test
    void rejectsRequestsThatWouldExceedTheBudget() {
        AtomicLong now = new AtomicLong(10L);
        DecompressionRateLimiter limiter = newLimiter(1024, now);

        limiter.consume(768);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> limiter.consume(257));

        assertEquals(
                "Decompression rate limit exceeded: requested 257 bytes with 768/1024 bytes already consumed in the current window",
                exception.getMessage());
    }

    @Test
    void resetsAfterTheOneSecondWindow() {
        AtomicLong now = new AtomicLong(10L);
        DecompressionRateLimiter limiter = newLimiter(1024, now);

        limiter.consume(1024);
        now.addAndGet(WINDOW_NANOS);

        assertDoesNotThrow(() -> limiter.consume(1024));
    }

    @Test
    void resetsIfTheClockMovesBackwards() {
        AtomicLong now = new AtomicLong(1_000L);
        DecompressionRateLimiter limiter = newLimiter(1024, now);

        limiter.consume(1024);
        now.set(500L);

        assertDoesNotThrow(() -> limiter.consume(1024));
    }

    @Test
    void zeroByteRequestsDoNotConsumeBudget() {
        AtomicLong now = new AtomicLong(10L);
        DecompressionRateLimiter limiter = newLimiter(1024, now);

        assertDoesNotThrow(() -> limiter.consume(0));
        assertDoesNotThrow(() -> limiter.consume(1024));
    }

    private static DecompressionRateLimiter newLimiter(long maxBytesPerSecond, AtomicLong now) {
        return new DecompressionRateLimiter(maxBytesPerSecond, now::get);
    }
}
