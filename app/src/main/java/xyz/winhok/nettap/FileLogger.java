package xyz.winhok.nettap;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.app.Application;
import android.content.Context;

import static xdroid.core.Global.getContext;
import static xdroid.core.ObjectUtils.notNull;


/**
 * Internal-storage append-only log file.
 *
 * <p>Hot path ({@link #logRawLine}) hands bytes off to a single-writer thread
 * through a bounded queue so host-app request threads never block on disk.
 * Overflow drops the oldest pending line (latest-wins) and bumps a counter.
 *
 * <p>Cold path ({@link #log}) stays synchronous for install diagnostics where
 * ordering and immediacy matter more than throughput.
 */
public class FileLogger {
    private static final int QUEUE_CAPACITY = 512;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final byte[] POISON = new byte[0];
    private static final byte[] NEWLINE = {'\n'};
    private final String filename;
    private final boolean rawOnly;
    /** Serializes {@link #bufferedOut} lifecycle (init/close) <em>and</em> every
     *  write against it, so the writer thread never writes through a stream the
     *  close path is concurrently nulling. */
    private final Object initLock = new Object();
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicLong droppedLines = new AtomicLong();

    private volatile BufferedOutputStream bufferedOut;

    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private volatile Thread writerThread;

    public FileLogger(String filename) {
        this(filename, false);
    }

    public FileLogger(String filename, boolean rawOnly) {
        this.filename = filename;
        this.rawOnly = rawOnly;
    }

    private void initialize() {
        if (this.bufferedOut != null) {
            return;
        }
        synchronized (initLock) {
            if (this.bufferedOut != null) {
                return;
            }
            try {
                Application app = (Application) notNull(getContext());
                FileOutputStream stream = app.openFileOutput(this.filename, Context.MODE_APPEND);
                this.bufferedOut = new BufferedOutputStream(stream, BUFFER_SIZE);
                registerShutdownHook();
            } catch (Exception e) {
                NetTap.getXposedLogger().log("failed to start logger: %s", e);
            }
        }
    }
    private void registerShutdownHook() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) {
            return;
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeQuietly,
                    "nettap-filelogger-shutdown-" + this.filename));
        } catch (Throwable ignored) {
            // Shutdown hooks may be rejected during JVM teardown.
        }
    }

    private void ensureWriter() {
        if (writerThread != null) {
            return;
        }
        synchronized (initLock) {
            if (writerThread != null) {
                return;
            }
            Thread t = new Thread(this::writerLoop, "nettap-filelogger-" + this.filename);
            t.setDaemon(true);
            writerThread = t;
            t.start();
        }
    }

    private void writerLoop() {
        while (true) {
            byte[] item;
            try {
                item = queue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            if (item == POISON) {
                break;
            }
            synchronized (initLock) {
                BufferedOutputStream out = this.bufferedOut;
                if (out == null) {
                    continue;
                }
                try {
                    out.write(item);
                    out.write(NEWLINE);
                    // Flush only when the queue is empty so bursty traffic
                    // amortizes through the 64KB buffer.
                    if (queue.isEmpty()) {
                        out.flush();
                    }
                } catch (Throwable e) {
                    NetTap.getXposedLogger().logSafe("filelogger writer failed: %s", e);
                }
            }
        }
    }
    /** Flush and close. Terminal — once called, further {@link #logRawLine} calls
     *  are dropped and the writer thread does not restart. Safe to call multiple times. */
    public void closeQuietly() {
        shuttingDown.set(true);
        Thread t = writerThread;
        if (t != null) {
            // Queue may be full; drop-then-offer in a loop until POISON lands.
            // shuttingDown gates new producers so this terminates quickly.
            while (!queue.offer(POISON)) {
                queue.poll();
            }
            try {
                t.join(500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (initLock) {
            BufferedOutputStream out = this.bufferedOut;
            if (out == null) {
                return;
            }
            try {
                out.flush();
                out.close();
            } catch (Exception ignored) {
            } finally {
                this.bufferedOut = null;
            }
        }
    }

    /** Synchronous timestamped log — install diagnostics only. */
    public void log(String message, Object... objects) {
        if (objects.length > 0) {
            message = String.format(message, objects);
        }
        if (this.rawOnly) {
            NetTap.getXposedLogger().log(message);
            return;
        }
        initialize();
        BufferedOutputStream out = this.bufferedOut;
        if (out == null) {
            return;
        }
        try {
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            byte[] bytes = String.format("%-31s %s\n", timestamp, message)
                    .getBytes(StandardCharsets.UTF_8);
            synchronized (initLock) {
                BufferedOutputStream current = this.bufferedOut;
                if (current == null) {
                    return;
                }
                current.write(bytes);
                current.flush();
            }
        } catch (Exception e) {
            NetTap.getXposedLogger().log("failed to log request URL: " + e);
        }
    }

    /** Append a raw line. Hot path — never blocks on disk I/O.
     *  Overflow drops the oldest pending line (latest-wins). */
    public void logRawLine(String line) {
        if (line == null || shuttingDown.get()) {
            return;
        }
        initialize();
        ensureWriter();

        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (queue.offer(bytes)) {
            return;
        }
        queue.poll();
        if (!queue.offer(bytes)) {
            droppedLines.incrementAndGet();
            return;
        }
        long dropped = droppedLines.incrementAndGet();
        if (dropped % 100L == 1L) {
            NetTap.getXposedLogger().logSafe(
                    "filelogger backpressure: %d lines dropped so far", dropped);
        }
    }

    long droppedLinesCount() {
        return droppedLines.get();
    }
}
