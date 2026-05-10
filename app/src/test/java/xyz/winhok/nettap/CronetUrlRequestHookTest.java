package xyz.winhok.nettap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public final class CronetUrlRequestHookTest {

    // The decode logic lives on CronetResponseBodyDecoder rather than directly
    // on CronetUrlRequestHook because the latter transitively links Xposed
    // APIs (compileOnly) that are absent from the unit-test classpath.
    // CronetUrlRequestHook delegates to this decoder at runtime.

    @Test
    public void decodeReturnsOmittedForBinaryContentType() {
        CaptureBody body = CronetResponseBodyDecoder.decodeCronetResponseBody(
                new byte[] { 1, 2, 3 }, "image/png", null, 3L, false);

        String json = body.toJson();
        assertTrue(
                "binary body must surface an omittedReason string, got: " + json,
                json.contains("\"omittedReason\":\"")
        );
        assertTrue(
                "binary body must not contain a decoded text payload, got: " + json,
                json.contains("\"text\":null")
        );
    }

    @Test
    public void decodeReturnsOmittedForUnknownContentType() {
        CaptureBody body = CronetResponseBodyDecoder.decodeCronetResponseBody(
                new byte[] { 1, 2, 3 }, "application/foo-bar", null, 3L, false);

        String json = body.toJson();
        assertTrue(
                "unknown content-type must be omitted, got: " + json,
                json.contains("\"omittedReason\":\"")
        );
        assertTrue(json.contains("\"text\":null"));
    }

    @Test
    public void decodeReturnsTextForTextualContentType() {
        byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);

        CaptureBody body = CronetResponseBodyDecoder.decodeCronetResponseBody(
                bytes, "application/json", null, bytes.length, false);

        String json = body.toJson();
        assertTrue(
                "expected decoded JSON text payload, got: " + json,
                json.contains("\"text\":\"{\\\"ok\\\":true}\"")
        );
        assertTrue(
                "expected truncated false, got: " + json,
                json.contains("\"truncated\":false")
        );
        assertTrue(
                "expected omittedReason null on text body, got: " + json,
                json.contains("\"omittedReason\":null")
        );
    }

    @Test
    public void decodeReturnsOmittedForEmptyBytes() {
        CaptureBody body = CronetResponseBodyDecoder.decodeCronetResponseBody(
                new byte[0], "application/json", null, 0L, false);

        String json = body.toJson();
        assertTrue(
                "empty bytes must produce an omittedReason, got: " + json,
                json.contains("\"omittedReason\":\"")
        );
        assertTrue(json.contains("\"text\":null"));
    }

    @Test
    public void decodeReturnsOmittedForNullBytes() {
        CaptureBody body = CronetResponseBodyDecoder.decodeCronetResponseBody(
                null, "application/json", null, 0L, false);

        String json = body.toJson();
        assertTrue(
                "null bytes must produce an omittedReason, got: " + json,
                json.contains("\"omittedReason\":\"")
        );
        assertTrue(json.contains("\"text\":null"));
    }

    @Test
    public void decodePropagatesTruncatedAndContentLength() {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        CaptureBody body = CronetResponseBodyDecoder.decodeCronetResponseBody(
                bytes, "text/plain", "identity", 9999L, true);

        String json = body.toJson();
        assertTrue(
                "expected truncated true, got: " + json,
                json.contains("\"truncated\":true")
        );
        assertTrue(
                "expected contentLength=9999 propagated, got: " + json,
                json.contains("\"contentLength\":9999")
        );
        assertTrue(
                "expected encoding=identity propagated, got: " + json,
                json.contains("\"encoding\":\"identity\"")
        );
        assertFalse(
                "encoding must not collapse to null, got: " + json,
                json.contains("\"encoding\":null")
        );
    }
}
