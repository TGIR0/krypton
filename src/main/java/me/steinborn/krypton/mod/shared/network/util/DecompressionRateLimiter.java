package me.steinborn.krypton.mod.shared.network.util;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Per-connection budget for claimed decompressed bytes.
 *
 * <p>The Minecraft compression format lets a peer declare the uncompressed
 * size before native decompression allocates the destination buffer. A hard
 * per-packet cap limits the size of one allocation, while this limiter also
 * limits how much decompressed data one connection may request in a rolling
 * one-second window.</p>
 */
public final class DecompressionRateLimiter {
    public static final String SYSTEM_PROPERTY = "krypton.max-decompressed-bytes-per-second";

    private static final long DEFAULT_MAX_BYTES_PER_SECOND = 128L * 1024L * 1024L;
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final long maxBytesPerSecond;
    private final LongSupplier nanoTime;

    private long windowStartNanos;
    private long consumedBytes;

    public DecompressionRateLimiter() {
        this(readConfiguredLimit(), System::nanoTime);
    }

    DecompressionRateLimiter(long maxBytesPerSecond, LongSupplier nanoTime) {
        if (maxBytesPerSecond <= 0) {
            throw new IllegalArgumentException("maxBytesPerSecond must be positive");
        }
        this.maxBytesPerSecond = maxBytesPerSecond;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.windowStartNanos = nanoTime.getAsLong();
    }

    /**
     * Reserves {@code bytes} from the current one-second decompression budget.
     *
     * @throws IllegalStateException if the request would exceed the budget
     */
    public void consume(int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
        if (bytes == 0) {
            return;
        }

        final long now = nanoTime.getAsLong();
        if (now < windowStartNanos || now - windowStartNanos >= WINDOW_NANOS) {
            windowStartNanos = now;
            consumedBytes = 0;
        }

        if (bytes > maxBytesPerSecond || consumedBytes > maxBytesPerSecond - bytes) {
            throw new IllegalStateException(
                    "Decompression rate limit exceeded: requested " + bytes
                            + " bytes with " + consumedBytes + "/" + maxBytesPerSecond
                            + " bytes already consumed in the current window");
        }

        consumedBytes += bytes;
    }

    static long defaultMaxBytesPerSecond() {
        return DEFAULT_MAX_BYTES_PER_SECOND;
    }

    private static long readConfiguredLimit() {
        final String configured = System.getProperty(SYSTEM_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_BYTES_PER_SECOND;
        }

        try {
            final long value = Long.parseLong(configured.trim());
            return value > 0 ? value : DEFAULT_MAX_BYTES_PER_SECOND;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_BYTES_PER_SECOND;
        }
    }
}
