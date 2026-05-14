package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RealtimeCaptureServerTest {
    @Test
    public void acceptsNdjsonLinesIntoSessionStore() throws Exception {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        RealtimeCaptureServer server = new RealtimeCaptureServer(
                0,
                line -> {
                    CaptureUiEvent event = CaptureNdjsonParser.parseLineOrNull(line);
                    return event != null && store.addFromRealtime(event);
                }
        );
        server.start();

        sendLine(server.getPort(), CaptureUiEventTest.sampleJson("https://socket.example", 200, null));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (store.sequenceOldestFirst().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertEquals(1, store.sequenceOldestFirst().size());
        assertEquals("socket.example", store.sequenceOldestFirst().get(0).getHost());
        server.close();
    }

    @Test
    public void countsCorruptLinesWithoutFailingServer() throws Exception {
        RealtimeCaptureServer server = new RealtimeCaptureServer(
                0,
                line -> CaptureNdjsonParser.parseLineOrNull(line) != null
        );
        server.start();

        sendLine(server.getPort(), "not-json");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (server.getCorruptLineCount() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertTrue(server.getCorruptLineCount() >= 1L);
        server.close();
    }

    @Test
    public void oversizedLinesAreDiscardedBeforeHandlerDispatch() throws Exception {
        AtomicInteger handlerCalls = new AtomicInteger();
        RealtimeCaptureServer server = new RealtimeCaptureServer(
                0,
                line -> {
                    handlerCalls.incrementAndGet();
                    return true;
                }
        );
        server.start();

        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i <= CaptureNdjsonParser.MAX_LINE_CHARS; i++) {
            oversized.append('x');
        }
        sendLine(server.getPort(), oversized.toString());

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (server.getCorruptLineCount() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertEquals(0, handlerCalls.get());
        assertEquals(1L, server.getOversizedLineCount());
        assertTrue(server.getCorruptLineCount() >= 1L);
        server.close();
    }

    @Test
    public void unterminatedLineIsDiscardedAndCountedAsCorrupt() throws Exception {
        AtomicInteger handlerCalls = new AtomicInteger();
        RealtimeCaptureServer server = new RealtimeCaptureServer(
                0,
                line -> {
                    handlerCalls.incrementAndGet();
                    return true;
                }
        );
        server.start();

        sendPartial(server.getPort(), "{\"id\":\"partial\"}");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (server.getCorruptLineCount() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertEquals(0, handlerCalls.get());
        assertTrue(server.getCorruptLineCount() >= 1L);
        server.close();
    }

    private static void sendLine(int port, String line) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                     socket.getOutputStream(),
                     StandardCharsets.UTF_8
             ))) {
            writer.print(line);
            writer.print('\n');
            writer.flush();
        }
    }

    private static void sendPartial(int port, String line) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                     socket.getOutputStream(),
                     StandardCharsets.UTF_8
             ))) {
            writer.print(line);
            writer.flush();
        }
    }
}
