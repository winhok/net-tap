package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CaptureUiEventTest {
    @Test
    public void parsesSchemaVersionTwoAndDerivedUrlFields() {
        String json = sampleJson(
                "https://api.example.com/v1/users?active=true",
                201,
                null
        );
        CaptureUiEvent event = CaptureUiEvent.fromJson(json);

        assertEquals("request-1", event.getId());
        assertEquals(json, event.getRawJson());
        assertEquals("api.example.com", event.getHost());
        assertEquals("/v1/users", event.getPath());
        assertEquals("active=true", event.getQuery());
        assertEquals("201", event.getDisplayStatus());
        assertEquals("application/json", event.getResponseHeaders().get("Content-Type"));
        assertEquals("{\"ok\":true}", event.getResponseBody().getText());
    }

    @Test
    public void invalidUrlFallsBackToSentinelHost() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson("", 0, "boom"));

        assertEquals(CaptureUiEvent.INVALID_HOST, event.getHost());
        assertEquals("(invalid URL)", event.getHostLabel());
        assertEquals("ERR", event.getDisplayStatus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedSchemaVersion() {
        CaptureUiEvent.fromJson(sampleJson(
                "https://example.com",
                200,
                null
        ).replace("\"schemaVersion\":2", "\"schemaVersion\":1"));
    }

    static String sampleJson(String url, int responseCode, String error) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"request-1\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"POST\","
                + "\"url\":\"" + url + "\","
                + "\"requestHeaders\":{\"Cookie\":\"sid=abc; theme=dark\"},"
                + "\"requestBody\":{\"contentType\":\"application/json\","
                + "\"contentLength\":7,"
                + "\"encoding\":null,"
                + "\"truncated\":false,"
                + "\"text\":\"{\\\"a\\\":1}\","
                + "\"omittedReason\":null},"
                + "\"responseCode\":" + responseCode + ","
                + "\"responseMessage\":\"Created\","
                + "\"responseHeaders\":{\"Content-Type\":\"application/json\","
                + "\"Set-Cookie\":\"id=1; Secure; HttpOnly; SameSite=Lax\"},"
                + "\"responseBody\":{\"contentType\":\"application/json\","
                + "\"contentLength\":11,"
                + "\"encoding\":null,"
                + "\"truncated\":false,"
                + "\"text\":\"{\\\"ok\\\":true}\","
                + "\"omittedReason\":null},"
                + "\"durationMs\":25,"
                + "\"error\":" + (error == null ? "null" : "\"" + error + "\"")
                + "}";
    }

    @Test
    public void approximateMemoryIncludesBodyText() {
        String json = sampleJson(
                "https://example.com",
                200,
                null
        );
        StringBuilder padded = new StringBuilder(json);
        for (int i = 0; i < 2000; i++) {
            padded.append(' ');
        }
        CaptureUiEvent event = CaptureUiEvent.fromJson(padded.toString());

        assertTrue(event.getApproximateBytes() >= "{\"ok\":true}".length());
        assertTrue(event.getApproximateBytes() >= padded.length() * 2L);
    }
}
