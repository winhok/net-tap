package xyz.winhok.nettap;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Input stream that mirrors every returned byte into a bounded capture
 * buffer. Fires {@link #onEnd} at most once, either when the underlying
 * stream reports EOF or when {@link #close()} is called.
 */
public final class TeeInputStream extends FilterInputStream {

    public interface EndCallback {
        void onEnd(byte[] captured, boolean truncated, long totalObserved);
    }

    private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    private final int cap;
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final EndCallback callback;
    private boolean truncated;
    private long totalObserved;

    public TeeInputStream(InputStream delegate, int cap, EndCallback callback) {
        super(delegate);
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.cap = Math.max(0, cap);
        this.callback = callback;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b == -1) {
            fire();
            return -1;
        }
        totalObserved++;
        if (capture.size() < cap) {
            capture.write(b);
        } else {
            truncated = true;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n == -1) {
            fire();
            return -1;
        }
        totalObserved += n;
        int remaining = cap - capture.size();
        if (remaining > 0) {
            int copy = Math.min(n, remaining);
            try {
                capture.write(b, off, copy);
            } catch (Throwable ignored) {
                truncated = true;
            }
            if (copy < n) {
                truncated = true;
            }
        } else if (n > 0) {
            truncated = true;
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            fire();
        }
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

    private void fire() {
        if (ended.compareAndSet(false, true) && callback != null) {
            try {
                callback.onEnd(capture.toByteArray(), truncated, totalObserved);
            } catch (Throwable ignored) {
            }
        }
    }
}
