package xyz.winhok.nettap.ui.data;

import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class RealtimeCaptureServer implements AutoCloseable {
    private static final int CLIENT_READ_TIMEOUT_MS = 2000;

    public interface LineHandler {
        boolean onLine(String line);
    }

    private final int requestedPort;
    private final LineHandler handler;
    private final AtomicLong corruptLineCount = new AtomicLong();
    private final AtomicLong oversizedLineCount = new AtomicLong();
    private final AtomicLong acceptedLineCount = new AtomicLong();
    private ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean closed;

    public RealtimeCaptureServer(int port, LineHandler handler) {
        this.requestedPort = port;
        this.handler = handler;
    }

    public synchronized void start() {
        if (serverSocket != null) {
            return;
        }
        try {
            serverSocket = new ServerSocket(
                    requestedPort,
                    50,
                    InetAddress.getByName("127.0.0.1")
            );
            executor = Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "nettap-realtime-server");
                thread.setDaemon(true);
                return thread;
            });
            executor.execute(this::acceptLoop);
        } catch (Throwable e) {
            close();
            throw new IllegalStateException("failed to start realtime server", e);
        }
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? requestedPort : socket.getLocalPort();
    }

    public long getCorruptLineCount() {
        return corruptLineCount.get();
    }

    public long getOversizedLineCount() {
        return oversizedLineCount.get();
    }

    public long getAcceptedLineCount() {
        return acceptedLineCount.get();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                ServerSocket currentServer = serverSocket;
                ExecutorService currentExecutor = executor;
                if (currentServer == null || currentExecutor == null) {
                    return;
                }
                Socket socket = currentServer.accept();
                currentExecutor.execute(() -> readClient(socket));
            } catch (Throwable e) {
                if (!closed) {
                    corruptLineCount.incrementAndGet();
                }
            }
        }
    }

    private void readClient(Socket socket) {
        try (Socket ignored = socket;
             InputStreamReader reader = new InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.UTF_8
             )) {
            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);
            readBoundedLines(reader);
        } catch (Throwable e) {
            corruptLineCount.incrementAndGet();
        }
    }

    private void readBoundedLines(InputStreamReader reader) throws java.io.IOException {
        StringBuilder line = new StringBuilder();
        boolean discardingOversizedLine = false;
        int value;
        while ((value = reader.read()) != -1) {
            char next = (char) value;
            if (next == '\r') {
                continue;
            }
            if (next == '\n') {
                if (!discardingOversizedLine) {
                    dispatchLine(line.toString());
                }
                line.setLength(0);
                discardingOversizedLine = false;
                continue;
            }
            if (discardingOversizedLine) {
                continue;
            }
            if (line.length() >= CaptureNdjsonParser.MAX_LINE_CHARS) {
                line.setLength(0);
                discardingOversizedLine = true;
                oversizedLineCount.incrementAndGet();
                corruptLineCount.incrementAndGet();
                continue;
            }
            line.append(next);
        }
        if (!discardingOversizedLine && line.length() > 0) {
            corruptLineCount.incrementAndGet();
        }
    }

    private void dispatchLine(String line) {
        boolean accepted = handler != null && handler.onLine(line);
        if (accepted) {
            acceptedLineCount.incrementAndGet();
        } else {
            corruptLineCount.incrementAndGet();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Throwable ignored) {
            }
            serverSocket = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
