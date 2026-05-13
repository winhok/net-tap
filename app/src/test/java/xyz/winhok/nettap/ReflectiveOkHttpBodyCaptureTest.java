package xyz.winhok.nettap;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.zip.GZIPOutputStream;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ReflectiveOkHttpBodyCaptureTest {
    @Test
    public void requestBodyCapturesTextualRealOkHttpBody() {
        Request request = new Request.Builder()
                .url("https://example.com/login")
                .post(RequestBody.create(
                        "{\"name\":\"win\"}".getBytes(StandardCharsets.UTF_8),
                        MediaType.get("application/json")
                ))
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertEquals(
                "{\"contentType\":\"application/json\",\"contentLength\":14,"
                        + "\"encoding\":null,\"truncated\":false,"
                        + "\"text\":\"{\\\"name\\\":\\\"win\\\"}\",\"omittedReason\":null}",
                body.toJson()
        );
    }

    @Test
    public void requestBodyCapturesFormBodyWhenWriteToFails() {
        Request request = new Request.Builder()
                .url("https://example.com/form")
                .post(new ThrowingFormLikeRequestBody())
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertEquals(
                "{\"contentType\":\"application/x-www-form-urlencoded\",\"contentLength\":17,"
                        + "\"encoding\":null,\"truncated\":false,"
                        + "\"text\":\"room_id=42&a=b+c\",\"omittedReason\":null}",
                body.toJson()
        );
    }

    @Test
    public void requestBodyCaptureFailureKeepsStableOmittedReason() {
        Request request = new Request.Builder()
                .url("https://example.com/text")
                .post(new ThrowingTextRequestBody())
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains(
                "\"omittedReason\":\"request body capture failed\""));
    }

    @Test
    public void requestBodyOmitsBinaryBody() {
        Request request = new Request.Builder()
                .url("https://example.com/upload")
                .post(RequestBody.create(new byte[] {1, 2, 3}, MediaType.get("image/png")))
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertNull(bodyText(body));
        assertTrue(body.toJson().contains("\"omittedReason\":\"binary request body omitted\""));
    }

    @Test
    public void requestBodyOmitsUnknownContentType() {
        Request request = new Request.Builder()
                .url("https://example.com/upload")
                .post(RequestBody.create(new byte[] {1, 2, 3}, null))
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains("\"omittedReason\":\"unknown request body content type omitted\""));
    }

    @Test
    public void requestBodySkipsKnownLargeBodyBeforeWriting() {
        Request request = new Request.Builder()
                .url("https://example.com/large")
                .post(new LargeRequestBody())
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains("\"omittedReason\":\"request body too large\""));
    }

    @Test
    public void responseBodyGunzipDecodesBeforeSerializing() throws Exception {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Encoding", "gzip");
        Response response = response(
                "https://example.com/data",
                Headers.of(headers),
                ResponseBody.create(gzip("{\"ok\":true}"), MediaType.get("application/json"))
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(response);

        assertEquals(
                "{\"contentType\":\"application/json\",\"contentLength\":31,"
                        + "\"encoding\":\"gzip\",\"truncated\":false,"
                        + "\"text\":\"{\\\"ok\\\":true}\",\"omittedReason\":null}",
                body.toJson()
        );
    }

    @Test
    public void responseBodyGunzipCapsDecodedOutput() throws Exception {
        String largeText = repeat('a', CaptureConfig.MAX_BODY_BYTES + 128);
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Encoding", "gzip");
        Response response = response(
                "https://example.com/data",
                Headers.of(headers),
                ResponseBody.create(gzip(largeText), MediaType.get("text/plain"))
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(response);
        String json = body.toJson();

        assertTrue(json.contains("\"truncated\":true"));
        assertTrue(json.length() < CaptureConfig.MAX_BODY_BYTES + 1000);
    }

    @Test
    public void responseBodyOmitsBinaryBody() {
        Response response = response(
                "https://example.com/image.png",
                Headers.of(new LinkedHashMap<String, String>()),
                ResponseBody.create(new byte[] {1, 2, 3}, MediaType.get("image/png"))
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(response);

        assertTrue(body.toJson().contains("\"omittedReason\":\"binary response body omitted\""));
    }

    @Test
    public void responseBodyOmitsUnknownContentType() {
        Response response = response(
                "https://example.com/data",
                Headers.of(new LinkedHashMap<String, String>()),
                ResponseBody.create(new byte[] {1, 2, 3}, null)
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(response);

        assertTrue(body.toJson().contains("\"omittedReason\":\"unknown response body content type omitted\""));
    }

    @Test
    public void responseCodeFallsBackToIntSignatureForObfuscatedResponse() {
        int actual = ReflectiveOkHttp.responseCode(new ObfuscatedResponse());

        assertEquals(418, actual);
    }

    @Test
    public void urlFallsBackToStringSignatureForObfuscatedRequest() {
        String actual = ReflectiveOkHttp.url(new ObfuscatedRequest());

        assertEquals("https://example.com/login", actual);
    }

    @Test
    public void responseMessageFallsBackToStringSignatureForObfuscatedResponse() {
        String actual = ReflectiveOkHttp.responseMessage(new ObfuscatedRequest());

        assertEquals("https://example.com/login", actual);
    }

    @Test
    public void requestMethodFallsBackToStringSignatureForObfuscatedRequest() {
        String actual = ReflectiveOkHttp.method(new ObfuscatedRequest());

        assertEquals("https://example.com/login", actual);
    }

    @Test
    public void responseCodeChoosesAlphabeticallyFirstMethodOnMultiMatch() {
        int actual = ReflectiveOkHttp.responseCode(new MultiMatchObfuscatedResponse());

        assertEquals(418, actual);
    }

    private static String bodyText(CaptureBody body) {
        String json = body.toJson();
        int start = json.indexOf("\"text\":");
        if (start < 0 || json.startsWith("\"text\":null", start)) {
            return null;
        }
        return json.substring(start);
    }

    private static byte[] gzip(String value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        gzip.write(value.getBytes(StandardCharsets.UTF_8));
        gzip.close();
        return output.toByteArray();
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static Response response(String url, Headers headers, ResponseBody body) {
        Request request = new Request.Builder().url(url).build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(headers)
                .body(body)
                .build();
    }

    private static final class LargeRequestBody extends RequestBody {
        public MediaType contentType() {
            return MediaType.get("application/json");
        }

        public long contentLength() {
            return CaptureConfig.MAX_BODY_BYTES + 1L;
        }

        public void writeTo(BufferedSink sink) throws IOException {
            throw new AssertionError("large request body should not be written");
        }
    }

    private static final class ThrowingFormLikeRequestBody extends RequestBody {
        public MediaType contentType() {
            return MediaType.get("application/x-www-form-urlencoded");
        }

        public long contentLength() {
            return 17L;
        }

        public int size() {
            return 2;
        }

        public String encodedName(int index) {
            return index == 0 ? "room_id" : "a";
        }

        public String encodedValue(int index) {
            return index == 0 ? "42" : "b+c";
        }

        public void writeTo(BufferedSink sink) throws IOException {
            throw new IOException("simulated reflective writeTo failure");
        }
    }

    private static final class ThrowingTextRequestBody extends RequestBody {
        public MediaType contentType() {
            return MediaType.get("text/plain");
        }

        public long contentLength() {
            return 5L;
        }

        public void writeTo(BufferedSink sink) throws IOException {
            throw new IOException("simulated write failure");
        }
    }

    /**
     * Stand-in for an R8/ProGuard-renamed {@code okhttp3.Response}: no method
     * literally named {@code code()}, but a non-static no-arg method that
     * returns {@code int} ({@code x()} here, mimicking a mangled accessor).
     */
    private static final class ObfuscatedResponse {
        public int x() {
            return 418;
        }
    }

    /**
     * Stand-in for an R8/ProGuard-renamed {@code okhttp3.Request}: no method
     * literally named {@code url()}, but a non-static no-arg method that
     * returns {@code String}.
     */
    private static final class ObfuscatedRequest {
        public String z() {
            return "https://example.com/login";
        }
    }

    /**
     * Two no-arg int-returning candidates declared in non-alphabetical order.
     * Verifies that the alphabetical tie-break in
     * {@code findNoArgMethodByReturnType} is deterministic regardless of JVM
     * declaration / iteration order.
     */
    private static final class MultiMatchObfuscatedResponse {
        public int b() {
            return 999;
        }

        public int a() {
            return 418;
        }
    }
}
