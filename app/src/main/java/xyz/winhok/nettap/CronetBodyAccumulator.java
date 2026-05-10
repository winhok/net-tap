package xyz.winhok.nettap;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Accumulates Cronet response body bytes across the multiple
 * {@code onReadCompleted(ByteBuffer, bytesRead, initialPosition, initialLimit, ...)}
 * callbacks that make up a single response.
 *
 * <p>Cronet hands each callback a {@link ByteBuffer} whose {@code position}
 * has already been advanced past the freshly written bytes (often to
 * {@code limit}). The actual payload lives at
 * {@code [initialPosition, initialPosition + bytesRead)}, accessed via
 * absolute reads so that the buffer's mutable state is never disturbed.
 *
 * <p>Once the accumulator's internal byte count reaches {@code maxBytes}
 * subsequent payload bytes are dropped and {@link #isTruncated()} flips to
 * {@code true}, while {@link #getTotalBytesObserved()} keeps counting the
 * full observed length. Callers should pass {@link CaptureConfig#MAX_BODY_BYTES}
 * for {@code maxBytes} unless they have a smaller per-call budget.
 *
 * <p>This class is package-private and intentionally not thread-safe; callers
 * (e.g. the Cronet hook) serialize access via the per-request {@code HookState}
 * lock.
 */
final class CronetBodyAccumulator {
    private static final int SCRATCH_SIZE = 8192;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private byte[] scratch;
    private long totalBytesObserved;
    private boolean truncated;

    void appendFromBuffer(
            ByteBuffer source,
            int bytesRead,
            int initialPosition,
            int initialLimit,
            int maxBytes
    ) {
        try {
            if (source == null || bytesRead <= 0) {
                return;
            }
            if (initialPosition < 0 || initialLimit < initialPosition) {
                return;
            }

            int capacity = source.capacity();
            if (initialPosition >= capacity) {
                return;
            }

            int safeLimit = Math.min(initialLimit, capacity);
            int available = safeLimit - initialPosition;
            int safeBytes = Math.min(bytesRead, available);
            if (safeBytes <= 0) {
                return;
            }

            totalBytesObserved += safeBytes;

            int remainingCapacity = maxBytes <= 0 ? 0 : Math.max(0, maxBytes - buffer.size());
            int copyCount = Math.min(safeBytes, remainingCapacity);
            if (copyCount > 0) {
                byte[] dst = scratch;
                if (dst == null) {
                    dst = new byte[SCRATCH_SIZE];
                    scratch = dst;
                }
                ByteBuffer view = source.duplicate();
                int offset = 0;
                while (offset < copyCount) {
                    int chunk = Math.min(dst.length, copyCount - offset);
                    view.position(initialPosition + offset);
                    view.limit(initialPosition + offset + chunk);
                    view.get(dst, 0, chunk);
                    buffer.write(dst, 0, chunk);
                    offset += chunk;
                }
            }
            if (copyCount < safeBytes) {
                truncated = true;
            }
        } catch (RuntimeException ignored) {
            // Xposed hooks must never break the host app callback on malformed
            // or vendor-specific ByteBuffer bounds.
        }
    }

    byte[] getBytes() {
        return buffer.toByteArray();
    }

    boolean isTruncated() {
        return truncated;
    }

    long getTotalBytesObserved() {
        return totalBytesObserved;
    }
}
