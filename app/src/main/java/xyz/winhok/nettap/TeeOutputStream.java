package xyz.winhok.nettap;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream that mirrors every byte it sees into a bounded capture buffer
 * while delegating writes to an underlying {@link OutputStream}. Once the
 * capture buffer hits {@link #capturedBytes()} {@code >=} cap, subsequent
 * bytes are dropped from the capture side (but still written through) and
 * {@link #isTruncated()} flips to true.
 *
 * <p>IO failures in the capture side never interfere with the underlying
 * write; they simply mark the stream truncated.
 */
public final class TeeOutputStream extends OutputStream {

    private final OutputStream delegate;
    private final java.io.ByteArrayOutputStream capture = new java.io.ByteArrayOutputStream();
    private final int cap;
    private boolean truncated;
    private long totalObserved;

    public TeeOutputStream(OutputStream delegate, int cap) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        this.cap = Math.max(0, cap);
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
        totalObserved++;
        if (capture.size() < cap) {
            try {
                capture.write(b);
            } catch (Throwable ignored) {
                truncated = true;
            }
        } else if (cap > 0) {
            truncated = true;
        } else {
            truncated = true;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
        totalObserved += len;
        int remaining = cap - capture.size();
        if (remaining <= 0) {
            if (len > 0) {
                truncated = true;
            }
            return;
        }
        int copy = Math.min(len, remaining);
        try {
            capture.write(b, off, copy);
        } catch (Throwable ignored) {
            truncated = true;
        }
        if (copy < len) {
            truncated = true;
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public byte[] capturedBytes() {
        return capture.toByteArray();
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long totalObserved() {
        return totalObserved;
    }
}
