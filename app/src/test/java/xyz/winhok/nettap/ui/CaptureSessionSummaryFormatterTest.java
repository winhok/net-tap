package xyz.winhok.nettap.ui;

import org.junit.Test;

import xyz.winhok.nettap.ui.data.CaptureSessionStore;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

import static org.junit.Assert.assertTrue;

public final class CaptureSessionSummaryFormatterTest {
    @Test
    public void formatsVisibleTotalMemoryAndState() {
        CaptureSessionStore store = new CaptureSessionStore(20, 1024L * 1024L);
        store.addFromRealtime(event("1"));
        store.addFromRealtime(event("2"));
        store.freeze();

        String summary = CaptureSessionSummaryFormatter.format(1, store);

        assertTrue(summary.startsWith("1 / 2 shown · "));
        assertTrue(summary.endsWith(" · frozen"));
    }

    private static CaptureUiEvent event(String id) {
        return CaptureUiEvent.fromJson("{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"" + id + "\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/" + id + "\","
                + "\"requestHeaders\":{},"
                + "\"requestBody\":{},"
                + "\"responseCode\":200,"
                + "\"responseMessage\":\"OK\","
                + "\"responseHeaders\":{},"
                + "\"responseBody\":{},"
                + "\"durationMs\":1,"
                + "\"error\":null"
                + "}");
    }
}
