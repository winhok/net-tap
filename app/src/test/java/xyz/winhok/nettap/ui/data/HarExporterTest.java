package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HarExporterTest {
    @Test
    public void exportsHarLogWithRequestResponseAndNetTapExtension() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/v1/users?active=true",
                201,
                null
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"version\":\"1.2\""));
        assertTrue(har.contains("\"method\":\"POST\""));
        assertTrue(har.contains("\"url\":\"https://api.example.com/v1/users?active=true\""));
        assertTrue(har.contains("\"status\":201"));
        assertTrue(har.contains("\"_nettap\""));
        assertTrue(har.contains("\"hook\":\"okhttp\""));
        assertTrue(har.contains("\"name\":\"active\""));
        assertTrue(har.contains("\"value\":\"true\""));
    }

    @Test
    public void mapsLocationHeaderToRedirectUrl() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/login",
                302,
                null
        ).replace(
                "\"Set-Cookie\":\"id=1; Secure; HttpOnly; SameSite=Lax\"",
                "\"Set-Cookie\":\"id=1; Secure; HttpOnly; SameSite=Lax\",\"Location\":\"https://api.example.com/home\""
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"redirectURL\":\"https://api.example.com/home\""));
    }

    @Test
    public void decodesQueryStringParameters() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/search?q=hello%20world&flag",
                200,
                null
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"name\":\"q\""));
        assertTrue(har.contains("\"value\":\"hello world\""));
        assertTrue(har.contains("\"name\":\"flag\""));
        assertTrue(har.contains("\"value\":\"\""));
    }

    @Test
    public void recordsRequestAndResponseTruncationInNetTapMetadata() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/upload",
                200,
                null
        ).replace(
                "\"requestBody\":{\"contentType\":\"application/json\",\"contentLength\":7,\"encoding\":null,\"truncated\":false,",
                "\"requestBody\":{\"contentType\":\"application/json\",\"contentLength\":7,\"encoding\":null,\"truncated\":true,"
        ).replace(
                "\"responseBody\":{\"contentType\":\"application/json\",\"contentLength\":11,\"encoding\":null,\"truncated\":false,",
                "\"responseBody\":{\"contentType\":\"application/json\",\"contentLength\":11,\"encoding\":null,\"truncated\":true,"
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"requestTruncated\":true"));
        assertTrue(har.contains("\"responseTruncated\":true"));
    }

    @Test
    public void marksTimingsAsAggregateNetTapTiming() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/timing",
                200,
                null
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"timings\":{\"send\":0,\"wait\":25,\"receive\":0,\"_nettap\":{\"note\":\"aggregate\"}}"));
    }

    @Test
    public void skipsInvalidEntriesAndRecordsSkippedCount() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/good",
                200,
                null
        ));

        String har = compact(HarExporter.export(Arrays.asList(event, null), false));

        assertTrue(har.contains("\"entries\":[{"));
        assertTrue(har.contains("\"url\":\"https://api.example.com/good\""));
        assertTrue(har.contains("\"skippedEntries\":1"));
    }

    @Test
    public void usesUnknownHarBodySizesForRequestAndResponse() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/body-size",
                200,
                null
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"request\":{\"method\":\"POST\""));
        assertTrue(har.contains("\"headersSize\":-1,\"bodySize\":-1,\"postData\""));
        assertTrue(har.contains("\"response\":{\"status\":200"));
        assertTrue(har.contains("\"headersSize\":-1,\"bodySize\":-1}"));
    }

    @Test
    public void usesHeaderContentTypeForPostDataAndResponseContentMimeType() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/mime",
                200,
                null
        ).replace(
                "\"requestHeaders\":{\"Cookie\":\"sid=abc; theme=dark\"}",
                "\"requestHeaders\":{\"Content-Type\":\"application/json\",\"Cookie\":\"sid=abc; theme=dark\"}"
        ).replace(
                "\"requestBody\":{\"contentType\":\"application/json\"",
                "\"requestBody\":{\"contentType\":null"
        ).replace(
                "\"responseBody\":{\"contentType\":\"application/json\"",
                "\"responseBody\":{\"contentType\":null"
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"postData\":{\"mimeType\":\"application/json\""));
        assertTrue(har.contains("\"content\":{\"size\":11,\"mimeType\":\"application/json\""));
    }

    @Test
    public void usesEmptyMimeTypeWhenHeadersAndBodyContentTypeAreMissing() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/no-mime",
                200,
                null
        ).replace(
                "\"requestHeaders\":{\"Cookie\":\"sid=abc; theme=dark\"}",
                "\"requestHeaders\":{\"Cookie\":\"sid=abc; theme=dark\"}"
        ).replace(
                "\"responseHeaders\":{\"Content-Type\":\"application/json\",",
                "\"responseHeaders\":{"
        ).replace(
                "\"requestBody\":{\"contentType\":\"application/json\"",
                "\"requestBody\":{\"contentType\":null"
        ).replace(
                "\"responseBody\":{\"contentType\":\"application/json\"",
                "\"responseBody\":{\"contentType\":null"
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"postData\":{\"mimeType\":\"\""));
        assertTrue(har.contains("\"content\":{\"size\":11,\"mimeType\":\"\""));
    }

    @Test
    public void usesMinusOneForUnknownResponseContentSize() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/unknown-size",
                200,
                null
        ).replace(
                "\"responseBody\":{\"contentType\":\"application/json\",\"contentLength\":11,",
                "\"responseBody\":{\"contentType\":\"application/json\",\"contentLength\":-1,"
        ));

        String har = compact(HarExporter.export(Arrays.asList(event), false));

        assertTrue(har.contains("\"content\":{\"size\":-1,\"mimeType\":\"application/json\""));
    }

    @Test
    public void exportsTwoSpaceIndentedHarJson() {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/pretty",
                200,
                null
        ));

        String har = HarExporter.export(Arrays.asList(event), false);

        assertTrue(har.startsWith("{\n  \"log\": {\n    \"version\": \"1.2\""));
    }

    @Test
    public void exportedHarParsesBackWithRequiredEntryFields() {
        CaptureUiEvent first = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/visible?active=true",
                201,
                null
        ));
        CaptureUiEvent second = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://cdn.example.com/all",
                404,
                "missing"
        ).replace("\"id\":\"request-1\"", "\"id\":\"request-2\""));

        Map<String, Object> root = SimpleJsonParser.parseObject(HarExporter.export(Arrays.asList(first, second), false));
        Map<String, Object> log = SimpleJsonParser.castMap(root.get("log"));
        List<?> entries = castList(log.get("entries"));

        assertEquals("1.2", log.get("version"));
        assertEquals(2, entries.size());

        Map<String, Object> entry = SimpleJsonParser.castMap(entries.get(0));
        Map<String, Object> request = SimpleJsonParser.castMap(entry.get("request"));
        Map<String, Object> response = SimpleJsonParser.castMap(entry.get("response"));
        Map<String, Object> content = SimpleJsonParser.castMap(response.get("content"));
        Map<String, Object> timings = SimpleJsonParser.castMap(entry.get("timings"));
        Map<String, Object> nettap = SimpleJsonParser.castMap(entry.get("_nettap"));

        assertEquals("POST", request.get("method"));
        assertEquals("https://api.example.com/visible?active=true", request.get("url"));
        assertEquals(201, ((Number) response.get("status")).intValue());
        assertEquals("{\"ok\":true}", content.get("text"));
        assertEquals(25, ((Number) timings.get("wait")).intValue());
        assertEquals("request-1", nettap.get("id"));
        assertEquals("okhttp", nettap.get("hook"));
    }

    private static String compact(String json) {
        return json.replace("\n", "")
                .replace("  ", "")
                .replace(": ", ":");
    }

    private static List<?> castList(Object value) {
        if (value instanceof List<?>) {
            return (List<?>) value;
        }
        throw new AssertionError("value is not a list: " + value);
    }
}
