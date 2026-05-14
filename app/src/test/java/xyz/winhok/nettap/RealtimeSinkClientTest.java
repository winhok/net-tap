package xyz.winhok.nettap;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RealtimeSinkClientTest {
    @Test
    public void sendNonBlockingWritesOneNdjsonLine() throws Exception {
        ServerSocket server = new ServerSocket(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> received = executor.submit(() -> {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(),
                         StandardCharsets.UTF_8
                 ))) {
                return reader.readLine();
            }
        });
        RealtimeSinkClient client = new RealtimeSinkClient(
                "127.0.0.1",
                server.getLocalPort(),
                4,
                50
        );

        assertTrue(client.sendNonBlocking("{\"id\":\"1\"}"));

        assertEquals("{\"id\":\"1\"}", received.get(2, TimeUnit.SECONDS));
        client.close();
        server.close();
        executor.shutdownNow();
    }

    @Test
    public void sendNonBlockingDropsOldestWhenQueueIsFull() {
        RealtimeSinkClient client = new RealtimeSinkClient("127.0.0.1", 9, 1, 1, false);

        assertTrue(client.sendNonBlocking("{\"id\":\"1\"}"));
        assertTrue(client.sendNonBlocking("{\"id\":\"2\"}"));

        assertTrue(client.getDroppedCount() >= 1L);
        client.close();
    }

    @Test
    public void closedPortFailureIsRecordedWithoutThrowingFromCaller() throws Exception {
        ServerSocket closed = new ServerSocket(0);
        int port = closed.getLocalPort();
        closed.close();
        RealtimeSinkClient client = new RealtimeSinkClient("127.0.0.1", port, 4, 10);

        assertTrue(client.sendNonBlocking("{\"id\":\"missing-listener\"}"));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (client.getFailedSendCount() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertTrue(client.getFailedSendCount() >= 1L);
        client.close();
    }

    @Test
    public void payloadNewlinesAreReplacedBeforeSend() throws Exception {
        ServerSocket server = new ServerSocket(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> received = executor.submit(() -> {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(),
                         StandardCharsets.UTF_8
                 ))) {
                return reader.readLine();
            }
        });
        RealtimeSinkClient client = new RealtimeSinkClient(
                "127.0.0.1",
                server.getLocalPort(),
                4,
                50
        );

        assertTrue(client.sendNonBlocking("{\"id\":\"1\"}\n{\"id\":\"2\"}"));

        assertEquals("{\"id\":\"1\"} {\"id\":\"2\"}", received.get(2, TimeUnit.SECONDS));
        client.close();
        server.close();
        executor.shutdownNow();
    }
}
