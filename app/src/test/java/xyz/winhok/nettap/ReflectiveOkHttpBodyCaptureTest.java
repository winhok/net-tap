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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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
    public void requestBodyOmitsUnknownLengthBeforeWriting() {
        Request request = new Request.Builder()
                .url("https://example.com/stream")
                .post(new UnknownLengthRequestBody())
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains(
                "\"omittedReason\":\"unknown request body length omitted\""));
    }

    @Test
    public void requestBodyOmitsOneShotBodyBeforeWriting() {
        Request request = new Request.Builder()
                .url("https://example.com/oneshot")
                .post(new OneShotRequestBody())
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains(
                "\"omittedReason\":\"one-shot request body omitted\""));
    }

    @Test
    public void requestBodyOmitsDuplexBodyBeforeWriting() {
        Request request = new Request.Builder()
                .url("https://example.com/duplex")
                .post(new DuplexRequestBody())
                .build();

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains(
                "\"omittedReason\":\"duplex request body omitted\""));
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
                Headers.of(new LinkedHashMap<>()),
                ResponseBody.create(new byte[] {1, 2, 3}, MediaType.get("image/png"))
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(response);

        assertTrue(body.toJson().contains("\"omittedReason\":\"binary response body omitted\""));
    }

    @Test
    public void responseBodyOmitsUnknownContentType() {
        Response response = response(
                "https://example.com/data",
                Headers.of(new LinkedHashMap<>()),
                ResponseBody.create(new byte[] {1, 2, 3}, null)
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(response);

        assertTrue(body.toJson().contains("\"omittedReason\":\"unknown response body content type omitted\""));
    }

    @Test
    public void responseBodyFallsBackToPeekBodyReaderWhenResponseHasNoPeekBodyMethod() {
        ResponseBody rawBody = ResponseBody.create(
                "fallback-body".getBytes(StandardCharsets.UTF_8),
                MediaType.get("text/plain")
        );

        CaptureBody body = ReflectiveOkHttp.responseBody(new MinimalResponse(rawBody));

        assertTrue(body.toJson().contains("\"text\":\"fallback-body\""));
        assertTrue(body.toJson().contains("\"contentLength\":13"));
    }

    @Test
    public void responseBodyFallbackOmitsWhenOriginalBodyIsMissing() {
        CaptureBody body = ReflectiveOkHttp.responseBody(new MinimalResponse(null));

        assertTrue(body.toJson().contains("\"omittedReason\":\"response body unavailable\""));
    }

    @Test
    public void nullRequestBodyIsOmittedWithoutThrowing() {
        CaptureBody body = ReflectiveOkHttp.requestBody(null);

        assertTrue(body.toJson().contains("\"omittedReason\":\"request body unavailable\""));
    }

    @Test
    public void nullResponseBodyIsOmittedWithoutThrowing() {
        CaptureBody body = ReflectiveOkHttp.responseBody(null);

        assertTrue(body.toJson().contains("\"omittedReason\":\"response body unavailable\""));
    }

    @Test
    public void responseBodyOmitsWhenPeekBodyReturnsNull() {
        CaptureBody body = ReflectiveOkHttp.responseBody(new NullPeekResponse());

        assertTrue(body.toJson().contains("\"omittedReason\":\"response body unavailable\""));
    }

    @Test
    public void responseBodyOmitsWhenPeekedBytesThrow() {
        CaptureBody body = ReflectiveOkHttp.responseBody(new ThrowingBytesResponse());

        assertTrue(body.toJson().contains("\"omittedReason\":\"response body unavailable\""));
    }

    @Test
    public void requestBodyCarriesContentEncodingFromHeadersField() {
        EncodedFieldRequest request = new EncodedFieldRequest(new ThrowingTextRequestBody());

        CaptureBody body = ReflectiveOkHttp.requestBody(request);

        assertTrue(body.toJson().contains("\"encoding\":\"br\""));
        assertTrue(body.toJson().contains("\"omittedReason\":\"request body capture failed\""));
    }

    @Test
    public void headersFallbackReadsNamesAndValuesFieldAndJoinsDuplicates() {
        LinkedHashMap<String, String> headers = ReflectiveOkHttp.headers(
                new NamesAndValuesHeaders(new String[] {
                        "Set-Cookie", "a=1",
                        "Set-Cookie", "b=2",
                        "Empty", null
                }));

        assertEquals("a=1\nb=2", headers.get("Set-Cookie"));
        assertNull(headers.get("Empty"));
    }

    @Test
    public void headersReadsSizeNameValueApiAndJoinsDuplicates() {
        LinkedHashMap<String, String> headers = ReflectiveOkHttp.headers(new IndexedHeaders());

        assertEquals("one\ntwo", headers.get("X-Test"));
        assertFalse(headers.containsKey(null));
    }

    @Test
    public void headersFallsBackToOwnerHeadersField() {
        LinkedHashMap<String, String> headers = ReflectiveOkHttp.headers(
                new HeadersOwner(new NamesAndValuesHeaders(new String[] {
                        "X-Owner", "yes"
                })));

        assertEquals("yes", headers.get("X-Owner"));
    }

    @Test
    public void headersReturnsEmptyWhenOwnerHeadersReturnsSelf() {
        LinkedHashMap<String, String> headers = ReflectiveOkHttp.headers(new SelfHeadersOwner());

        assertTrue(headers.isEmpty());
    }

    @Test
    public void requestForResponseFallsBackWhenResponseHasNoRequest() {
        Object fallback = new Object();

        Object actual = ReflectiveOkHttp.requestForResponse(new Object(), fallback);

        assertSame(fallback, actual);
    }

    @Test
    public void requestForResponsePrefersResponseRequestWhenPresent() {
        Object request = new Object();

        Object actual = ReflectiveOkHttp.requestForResponse(new ResponseWithRequest(request), new Object());

        assertSame(request, actual);
    }

    @Test
    public void scalarAccessorsReadFieldsAndDefaults() {
        FieldBackedExchange exchange = new FieldBackedExchange();

        assertEquals("POST", ReflectiveOkHttp.method(exchange));
        assertEquals("https://example.com/field", ReflectiveOkHttp.url(exchange));
        assertEquals(201, ReflectiveOkHttp.responseCode(exchange));
        assertEquals("Created", ReflectiveOkHttp.responseMessage(exchange));
        assertEquals("", ReflectiveOkHttp.method(null));
        assertEquals("", ReflectiveOkHttp.url(null));
        assertEquals(0, ReflectiveOkHttp.responseCode(new InvalidCodeResponse()));
        assertEquals("", ReflectiveOkHttp.responseMessage(null));
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

    private static final class UnknownLengthRequestBody extends RequestBody {
        public MediaType contentType() {
            return MediaType.get("text/plain");
        }

        public long contentLength() {
            return -1L;
        }

        public void writeTo(BufferedSink sink) throws IOException {
            throw new AssertionError("unknown length request body should not be written");
        }
    }

    private static final class OneShotRequestBody extends RequestBody {
        public MediaType contentType() {
            return MediaType.get("text/plain");
        }

        public long contentLength() {
            return 3L;
        }

        public boolean isOneShot() {
            return true;
        }

        public void writeTo(BufferedSink sink) throws IOException {
            throw new AssertionError("one-shot request body should not be written");
        }
    }

    private static final class DuplexRequestBody extends RequestBody {
        public MediaType contentType() {
            return MediaType.get("text/plain");
        }

        public long contentLength() {
            return 3L;
        }

        public boolean isDuplex() {
            return true;
        }

        public void writeTo(BufferedSink sink) throws IOException {
            throw new AssertionError("duplex request body should not be written");
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

    private static final class MinimalResponse {
        private final ResponseBody body;

        MinimalResponse(ResponseBody body) {
            this.body = body;
        }

        public ResponseBody body() {
            return body;
        }

        public NamesAndValuesHeaders headers() {
            return new NamesAndValuesHeaders(new String[] {
                    "Content-Encoding", "identity"
            });
        }
    }

    private static final class NullPeekResponse {
        public ResponseBody body() {
            return ResponseBody.create("hello".getBytes(StandardCharsets.UTF_8),
                    MediaType.get("text/plain"));
        }

        public Object peekBody(long byteCount) {
            return null;
        }

        public NamesAndValuesHeaders headers() {
            return new NamesAndValuesHeaders(new String[] {
                    "Content-Encoding", "identity"
            });
        }
    }

    private static final class ThrowingBytesResponse {
        public ThrowingBytesBody body() {
            return new ThrowingBytesBody();
        }

        public ThrowingBytesBody peekBody(long byteCount) {
            return new ThrowingBytesBody();
        }

        public NamesAndValuesHeaders headers() {
            return new NamesAndValuesHeaders(new String[] {
                    "Content-Encoding", "identity"
            });
        }
    }

    private static final class ThrowingBytesBody {
        public String contentType() {
            return "text/plain";
        }

        public long contentLength() {
            return 5L;
        }

        public byte[] bytes() {
            throw new IllegalStateException("simulated bytes failure");
        }
    }

    private static final class EncodedFieldRequest {
        public final NamesAndValuesHeaders headers = new NamesAndValuesHeaders(new String[] {
                "Content-Encoding", "br"
        });
        private final RequestBody body;

        EncodedFieldRequest(RequestBody body) {
            this.body = body;
        }

        public RequestBody body() {
            return body;
        }
    }

    private static final class IndexedHeaders {
        public int size() {
            return 3;
        }

        public String name(int index) {
            return index == 2 ? null : "X-Test";
        }

        public String value(int index) {
            return index == 0 ? "one" : "two";
        }
    }

    private static final class HeadersOwner {
        private final Object headers;

        HeadersOwner(Object headers) {
            this.headers = headers;
        }

        public Object headers() {
            return headers;
        }
    }

    private static final class SelfHeadersOwner {
        public Object headers() {
            return this;
        }
    }

    private static final class ResponseWithRequest {
        private final Object request;

        ResponseWithRequest(Object request) {
            this.request = request;
        }

        public Object request() {
            return request;
        }
    }

    private static final class FieldBackedExchange {
        public String method = "POST";
        public String url = "https://example.com/field";
        public String code = "201";
        public String message = "Created";
    }

    private static final class InvalidCodeResponse {
        public String code = "not-a-number";
    }

    private static final class NamesAndValuesHeaders {
        @SuppressWarnings("unused")
        private final String[] namesAndValues;

        NamesAndValuesHeaders(String[] namesAndValues) {
            this.namesAndValues = namesAndValues;
        }
    }
}
