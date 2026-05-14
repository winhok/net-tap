package xyz.winhok.nettap;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class RealtimeSinkClient implements AutoCloseable {
    private static final Object DEFAULT_LOCK = new Object();
    private static volatile RealtimeSinkClient defaultClient;

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final LinkedBlockingDeque<String> queue;
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong failedSendCount = new AtomicLong();
    private final Thread worker;
    private volatile boolean closed;

    public RealtimeSinkClient(String host, int port, int queueCapacity, int timeoutMs) {
        this(host, port, queueCapacity, timeoutMs, true);
    }

    RealtimeSinkClient(String host, int port, int queueCapacity, int timeoutMs, boolean autoStart) {
        this.host = host == null ? "127.0.0.1" : host;
        this.port = port;
        this.timeoutMs = Math.max(1, timeoutMs);
        this.queue = new LinkedBlockingDeque<>(Math.max(1, queueCapacity));
        this.worker = new Thread(this::drainLoop, "nettap-realtime-sink");
        this.worker.setDaemon(true);
        if (autoStart) {
            this.worker.start();
        }
    }

    public static boolean sendDefault(String json) {
        if (!RuntimeCaptureConfig.isRealtimeTransportEnabled()) {
            return false;
        }
        return defaultClient().sendNonBlocking(json);
    }

    public static void reconfigureDefault() {
        synchronized (DEFAULT_LOCK) {
            if (defaultClient != null) {
                defaultClient.close();
                defaultClient = null;
            }
        }
    }

    private static RealtimeSinkClient defaultClient() {
        RealtimeSinkClient existing = defaultClient;
        if (existing != null) {
            return existing;
        }
        synchronized (DEFAULT_LOCK) {
            if (defaultClient == null) {
                defaultClient = new RealtimeSinkClient(
                        "127.0.0.1",
                        RuntimeCaptureConfig.getRealtimePort(),
                        RuntimeCaptureConfig.getRealtimeQueueCapacity(),
                        RuntimeCaptureConfig.getRealtimeTimeoutMs()
                );
            }
            return defaultClient;
        }
    }

    public boolean sendNonBlocking(String json) {
        if (closed || json == null) {
            return false;
        }
        String line = json.indexOf('\n') >= 0 ? json.replace('\n', ' ') : json;
        if (queue.offerLast(line)) {
            return true;
        }
        queue.pollFirst();
        droppedCount.incrementAndGet();
        return queue.offerLast(line);
    }

    public long getDroppedCount() {
        return droppedCount.get();
    }

    public long getFailedSendCount() {
        return failedSendCount.get();
    }

    private void drainLoop() {
        while (!closed) {
            try {
                String line = queue.poll(250, TimeUnit.MILLISECONDS);
                if (line != null) {
                    sendLine(line);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable e) {
                failedSendCount.incrementAndGet();
            }
        }
    }

    private void sendLine(String line) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets.UTF_8
            ))) {
                writer.print(line);
                writer.print('\n');
                writer.flush();
            }
        } catch (Throwable e) {
            failedSendCount.incrementAndGet();
        }
    }

    @Override
    public void close() {
        closed = true;
        if (worker.isAlive()) {
            worker.interrupt();
        }
    }
}
