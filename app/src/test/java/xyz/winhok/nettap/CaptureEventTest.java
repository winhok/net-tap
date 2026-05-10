package xyz.winhok.nettap;

import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public final class CaptureEventTest {
    @Test
    public void completeSerializesRequestAndResponseBodies() {
        LinkedHashMap<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("X-Request-Null", null);
        requestHeaders.put(null, "skipped");

        LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("Content-Type", "application/json");
        responseHeaders.put(null, "skipped");

        CaptureEvent event = CaptureEvent.complete(
                "1",
                "2026-05-08T00:00:00.000Z",
                "com.example",
                "RealInterceptorChain.proceed",
                "POST",
                "https://example.com/login",
                requestHeaders,
                CaptureBody.text("application/json", 7, null, false, "{\"a\":1}"),
                200,
                "OK",
                responseHeaders,
                CaptureBody.text("application/json", 11, null, false, "{\"ok\":true}"),
                153,
                null
        );

        String json = event.toJson();

        assertEquals(
                "{\"id\":\"1\",\"timestamp\":\"2026-05-08T00:00:00.000Z\","
                        + "\"packageName\":\"com.example\","
                        + "\"okhttpHook\":\"RealInterceptorChain.proceed\","
                        + "\"method\":\"POST\","
                        + "\"url\":\"https://example.com/login\","
                        + "\"requestHeaders\":{\"Content-Type\":\"application/json\","
                        + "\"X-Request-Null\":null},"
                        + "\"requestBody\":{\"contentType\":\"application/json\","
                        + "\"contentLength\":7,"
                        + "\"encoding\":null,"
                        + "\"truncated\":false,"
                        + "\"text\":\"{\\\"a\\\":1}\","
                        + "\"omittedReason\":null},"
                        + "\"responseCode\":200,"
                        + "\"responseMessage\":\"OK\","
                        + "\"responseHeaders\":{\"Content-Type\":\"application/json\"},"
                        + "\"responseBody\":{\"contentType\":\"application/json\","
                        + "\"contentLength\":11,"
                        + "\"encoding\":null,"
                        + "\"truncated\":false,"
                        + "\"text\":\"{\\\"ok\\\":true}\","
                        + "\"omittedReason\":null},"
                        + "\"durationMs\":153,"
                        + "\"error\":null}",
                json
        );
    }
}
