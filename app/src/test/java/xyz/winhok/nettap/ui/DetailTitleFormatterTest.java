package xyz.winhok.nettap.ui;

import org.junit.Test;

import xyz.winhok.nettap.ui.data.CaptureUiEvent;

import static org.junit.Assert.assertEquals;

public final class DetailTitleFormatterTest {
    @Test
    public void formatsMethodHostAndPath() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson(
                "https://api.example.com/v1/items?q=1",
                200,
                null
        ));

        assertEquals("GET api.example.com/v1/items", DetailTitleFormatter.title(event));
    }

    @Test
    public void formatsInvalidUrlWithLabel() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(sampleJson(
                "",
                200,
                null
        ));

        assertEquals("GET (invalid URL)", DetailTitleFormatter.title(event));
    }

    private static String sampleJson(String url, int status, String error) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"request-1\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"GET\","
                + "\"url\":\"" + url + "\","
                + "\"requestHeaders\":{},"
                + "\"requestBody\":{},"
                + "\"responseCode\":" + status + ","
                + "\"responseMessage\":\"OK\","
                + "\"responseHeaders\":{},"
                + "\"responseBody\":{},"
                + "\"durationMs\":1,"
                + "\"error\":" + (error == null ? "null" : "\"" + error + "\"")
                + "}";
    }
}
