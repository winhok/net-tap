package xyz.winhok.nettap;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import android.app.Application;
import android.content.Context;

import static xdroid.core.Global.getContext;


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
    enum WriteResult {
        ENQUEUED,
        WRITTEN_SYNC,
        DROPPED,
        UNAVAILABLE;

        /** Logcat must carry the payload when the file write didn't durably land. */
        boolean requiresLogcatFallback(boolean oversized) {
            if (this == UNAVAILABLE || this == DROPPED) {
                return true;
            }
            return oversized && this != WRITTEN_SYNC;
        }
    }

    interface ContextProvider {
        Context currentContext();
    }

    interface FailureReporter {
        void report(String message, Throwable throwable);
    }

    private static final int QUEUE_CAPACITY = 512;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long SYNC_WRITE_TIMEOUT_MS = 2_000L;
    private static final WriteItem POISON = new WriteItem(new byte[0], false);
    private static final byte[] NEWLINE = {'\n'};
    private final String filename;
    private final boolean rawOnly;
    private final ContextProvider contextProvider;
    private final FailureReporter failureReporter;
    /** Serializes {@link #bufferedOut} lifecycle (init/close) <em>and</em> every
     *  write against it, so the writer thread never writes through a stream the
     *  close path is concurrently nulling. */
    private final Object initLock = new Object();
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final AtomicBoolean startFailureLogged = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean writerHealthy = new AtomicBoolean(true);
    private final AtomicLong droppedLines = new AtomicLong();

    private volatile BufferedOutputStream bufferedOut;

    private final BlockingQueue<WriteItem> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private volatile Thread writerThread;

    public FileLogger(String filename) {
        this(filename, false);
    }

    public FileLogger(String filename, boolean rawOnly) {
        this(filename, rawOnly, new DefaultContextProvider(), new XposedFailureReporter());
    }

    FileLogger(
            String filename,
            boolean rawOnly,
            ContextProvider contextProvider,
            FailureReporter failureReporter
    ) {
        this.filename = filename;
        this.rawOnly = rawOnly;
        this.contextProvider = contextProvider;
        this.failureReporter = failureReporter;
    }

    private boolean initialize() {
        if (!writerHealthy.get()) {
            return false;
        }
        if (this.bufferedOut != null) {
            return true;
        }
        synchronized (initLock) {
            if (!writerHealthy.get()) {
                return false;
            }
            if (this.bufferedOut != null) {
                return true;
            }
            try {
                Context context = contextProvider.currentContext();
                if (context == null) {
                    reportStartFailure("context unavailable", null);
                    return false;
                }
                FileOutputStream stream = context.openFileOutput(this.filename, Context.MODE_APPEND);
                this.bufferedOut = new BufferedOutputStream(stream, BUFFER_SIZE);
                registerShutdownHook();
                return true;
            } catch (Exception e) {
                reportStartFailure("openFileOutput failed", e);
                return false;
            }
        }
    }

    private void reportStartFailure(String message, Throwable throwable) {
        if (!startFailureLogged.compareAndSet(false, true)) {
            return;
        }
        if (failureReporter != null) {
            failureReporter.report(message, throwable);
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
            WriteItem item;
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
                    item.complete(WriteResult.UNAVAILABLE);
                    continue;
                }
                try {
                    out.write(item.bytes);
                    out.write(NEWLINE);
                    // Flush only when the queue is empty so bursty traffic
                    // amortizes through the 64KB buffer. Synchronous writes
                    // always flush because their caller waits for durability.
                    if (item.sync || queue.isEmpty()) {
                        out.flush();
                    }
                    item.complete(item.sync ? WriteResult.WRITTEN_SYNC : WriteResult.ENQUEUED);
                } catch (Throwable e) {
                    safeXposedLog("filelogger writer failed: %s", e);
                    item.complete(WriteResult.UNAVAILABLE);
                    markWriteFailed(e);
                }
            }
        }
    }

    private void markWriteFailed(Throwable throwable) {
        writerHealthy.set(false);
        reportStartFailure("write failed", throwable);
        closeOutputQuietly();
    }

    private void closeOutputQuietly() {
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

    /** Flush and close. Terminal — once called, further {@link #logRawLine} calls
     *  are dropped and the writer thread does not restart. Safe to call multiple times. */
    public void closeQuietly() {
        shuttingDown.set(true);
        Thread t = writerThread;
        if (t != null) {
            // shuttingDown gates new producers so this terminates quickly.
            while (!queue.offer(POISON)) {
                WriteItem dropped = queue.poll();
                if (dropped != null) {
                    dropped.complete(WriteResult.DROPPED);
                }
            }
            try {
                t.join(500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            // Release any sync caller whose item the writer abandoned after POISON.
            WriteItem leftover;
            while ((leftover = queue.poll()) != null) {
                if (leftover != POISON) {
                    leftover.complete(WriteResult.UNAVAILABLE);
                }
            }
        }
        closeOutputQuietly();
    }

    /** Synchronous timestamped log — install diagnostics only. */
    public void log(String message, Object... objects) {
        if (objects.length > 0) {
            message = String.format(message, objects);
        }
        if (this.rawOnly) {
            safeXposedLog(message);
            return;
        }
        if (!initialize()) {
            return;
        }
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
            safeXposedLog("failed to log request URL: %s", e);
            markWriteFailed(e);
        }
    }

    /** Append a raw line. Hot path — never blocks on disk I/O.
     *  Overflow drops the oldest pending line (latest-wins). */
    public WriteResult logRawLine(String line) {
        return append(line, false);
    }

    public WriteResult logRawLineBlocking(String line) {
        return append(line, true);
    }

    private WriteResult append(String line, boolean sync) {
        if (line == null || shuttingDown.get()) {
            return WriteResult.UNAVAILABLE;
        }
        if (!initialize()) {
            return WriteResult.UNAVAILABLE;
        }
        ensureWriter();

        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        WriteItem item = new WriteItem(bytes, sync);
        WriteResult enqueueResult = enqueue(item);
        if (!sync || enqueueResult != WriteResult.ENQUEUED) {
            return enqueueResult;
        }
        try {
            if (!item.await(SYNC_WRITE_TIMEOUT_MS)) {
                return WriteResult.ENQUEUED;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return WriteResult.ENQUEUED;
        }
        WriteResult result = item.result;
        return result == null ? WriteResult.ENQUEUED : result;
    }

    private WriteResult enqueue(WriteItem item) {
        if (queue.offer(item)) {
            return WriteResult.ENQUEUED;
        }
        WriteItem dropped = queue.poll();
        if (dropped != null) {
            dropped.complete(WriteResult.DROPPED);
        }
        if (!queue.offer(item)) {
            droppedLines.incrementAndGet();
            item.complete(WriteResult.DROPPED);
            return WriteResult.DROPPED;
        }
        long droppedCount = droppedLines.incrementAndGet();
        if (droppedCount % 100L == 1L) {
            safeXposedLog("filelogger backpressure: %d lines dropped so far", droppedCount);
        }
        return WriteResult.ENQUEUED;
    }

    long droppedLinesCount() {
        return droppedLines.get();
    }

    private static final class DefaultContextProvider implements ContextProvider {
        private static volatile Method cachedCurrentApplication;

        @Override
        public Context currentContext() {
            Context context = currentApplicationViaXposed();
            if (context != null) {
                return context;
            }
            try {
                return getContext();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Context currentApplicationViaXposed() {
            try {
                Method method = cachedCurrentApplication;
                if (method == null) {
                    Class<?> helper = Class.forName("de.robv.android.xposed.AndroidAppHelper");
                    method = helper.getDeclaredMethod("currentApplication");
                    cachedCurrentApplication = method;
                }
                Object value = method.invoke(null);
                if (value instanceof Application) {
                    return (Application) value;
                }
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    private static void safeXposedLog(String message, Object... args) {
        try {
            NetTap.getXposedLogger().logSafe(message, args);
        } catch (Throwable ignored) {
        }
    }

    private static final class XposedFailureReporter implements FailureReporter {
        @Override
        public void report(String message, Throwable throwable) {
            String detail = throwable == null ? "" : "\n" + android.util.Log.getStackTraceString(throwable);
            NetTap.getXposedLogger().logSafe("failed to start logger: %s%s", message, detail);
        }
    }

    private static final class WriteItem {
        private final byte[] bytes;
        private final boolean sync;
        private final CountDownLatch latch;
        private volatile WriteResult result;

        private WriteItem(byte[] bytes, boolean sync) {
            this.bytes = bytes;
            this.sync = sync;
            this.latch = sync ? new CountDownLatch(1) : null;
        }

        private void complete(WriteResult result) {
            this.result = result;
            if (latch != null) {
                latch.countDown();
            }
        }

        private boolean await(long timeoutMs) throws InterruptedException {
            if (latch == null) {
                return true;
            }
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }
}
