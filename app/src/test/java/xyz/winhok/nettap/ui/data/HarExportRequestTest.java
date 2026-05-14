package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HarExportRequestTest {
    @Test
    public void snapshotsEventsForRetry() {
        List<CaptureUiEvent> events = new ArrayList<>();
        events.add(CaptureUiEvent.fromJson(sampleJson("request-1")));

        HarExportRequest request = HarExportRequest.from(events);
        events.clear();

        assertEquals(1, request.events().size());
        assertEquals("request-1", request.events().get(0).getId());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void exposesImmutableEvents() {
        HarExportRequest request = HarExportRequest.from(Collections.emptyList());

        request.events().add(CaptureUiEvent.fromJson(sampleJson("request-2")));
    }

    @Test
    public void nullEventsBecomeEmptySnapshot() {
        HarExportRequest request = HarExportRequest.from(null);

        assertTrue(request.events().isEmpty());
    }

    private static String sampleJson(String id) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"" + id + "\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com\","
                + "\"requestHeaders\":{},"
                + "\"requestBody\":{},"
                + "\"responseCode\":200,"
                + "\"responseMessage\":\"OK\","
                + "\"responseHeaders\":{},"
                + "\"responseBody\":{},"
                + "\"durationMs\":1,"
                + "\"error\":null"
                + "}";
    }
}
