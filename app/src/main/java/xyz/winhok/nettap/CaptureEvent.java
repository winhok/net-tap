package xyz.winhok.nettap;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CaptureEvent {

    // v2: field `okhttpHook` renamed to `hook` (v1 was implicit — no schemaVersion was emitted).
    private static final int SCHEMA_VERSION = 2;

    private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_FORMAT = new ThreadLocal<>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return newTimestampFormat();
        }
    };

    private static final ConcurrentHashMap<String, AtomicLong> ID_COUNTERS = new ConcurrentHashMap<>();

    /** Current wall-clock timestamp in the project's fixed UTC millisecond format. */
    public static String timestampNow() {
        SimpleDateFormat format = TIMESTAMP_FORMAT.get();
        if (format == null) {
            format = newTimestampFormat();
            TIMESTAMP_FORMAT.set(format);
        }
        return format.format(new Date(System.currentTimeMillis()));
    }

    private static SimpleDateFormat newTimestampFormat() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt;
    }

    /** Elapsed milliseconds since {@code startNanos}, clamped to zero. */
    public static long elapsedMsSince(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    /** Per-prefix monotonically-increasing id (e.g. {@code "hurl-7"}). */
    public static String nextId(String prefix) {
        AtomicLong counter = ID_COUNTERS.get(prefix);
        if (counter == null) {
            counter = ID_COUNTERS.computeIfAbsent(prefix, k -> new AtomicLong());
        }
        return prefix + "-" + counter.incrementAndGet();
    }

    private final String id;
    private final String timestamp;
    private final String packageName;
    private final String hook;
    private final String method;
    private final String url;
    private final Map<String, String> requestHeaders;
    private final CaptureBody requestBody;
    private final int responseCode;
    private final String responseMessage;
    private final Map<String, String> responseHeaders;
    private final CaptureBody responseBody;
    private final long durationMs;
    private final String error;

    private CaptureEvent(
            String id,
            String timestamp,
            String packageName,
            String hook,
            String method,
            String url,
            LinkedHashMap<String, String> requestHeaders,
            CaptureBody requestBody,
            int responseCode,
            String responseMessage,
            LinkedHashMap<String, String> responseHeaders,
            CaptureBody responseBody,
            long durationMs,
            String error
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.packageName = packageName;
        this.hook = hook;
        this.method = method;
        this.url = url;
        this.requestHeaders = copyHeaders(requestHeaders);
        this.requestBody = requestBody;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.responseHeaders = copyHeaders(responseHeaders);
        this.responseBody = responseBody;
        this.durationMs = durationMs;
        this.error = error;
    }

    /** Factory for hooks that have already parsed all fields into primitives. */
    public static CaptureEvent fromParsed(
            String id,
            String timestamp,
            String packageName,
            String hook,
            String method,
            String url,
            LinkedHashMap<String, String> requestHeaders,
            CaptureBody requestBody,
            int responseCode,
            String responseMessage,
            LinkedHashMap<String, String> responseHeaders,
            CaptureBody responseBody,
            long durationMs,
            String error
    ) {
        return new CaptureEvent(
                id,
                timestamp,
                packageName,
                hook,
                method,
                url,
                requestHeaders,
                requestBody,
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                durationMs,
                error
        );
    }

    /**
     * Narrow factory for OkHttp-family hooks. Handles nullable {@code response}
     * (error path) by defaulting code=0, message="", empty headers, and an
     * omitted response body. Pulls request/response fields via
     * {@link ReflectiveOkHttp} so call sites stay ~5 lines.
     */
    public static CaptureEvent fromOkHttpCall(
            String id,
            String timestamp,
            String packageName,
            String hook,
            Object request,
            Object response,
            long durationMs,
            Throwable throwable
    ) {
        return new CaptureEvent(
                id,
                timestamp,
                packageName,
                hook,
                ReflectiveOkHttp.method(request),
                ReflectiveOkHttp.url(request),
                ReflectiveOkHttp.headers(request),
                ReflectiveOkHttp.requestBody(request),
                response == null ? 0 : ReflectiveOkHttp.responseCode(response),
                response == null ? "" : ReflectiveOkHttp.responseMessage(response),
                response == null ? new LinkedHashMap<>() : ReflectiveOkHttp.headers(response),
                response == null
                        ? CaptureBody.omitted(null, -1L, null, "response unavailable")
                        : ReflectiveOkHttp.responseBody(response),
                durationMs,
                throwable == null ? null : String.valueOf(throwable)
        );
    }

    public String toJson() {
        StringBuilder result = new StringBuilder();
        result.append('{');
        appendField(result, "schemaVersion", JsonWriter.number(SCHEMA_VERSION), true);
        appendField(result, "id", JsonWriter.string(id), false);
        appendField(result, "timestamp", JsonWriter.string(timestamp), false);
        appendField(result, "packageName", JsonWriter.string(packageName), false);
        appendField(result, "hook", JsonWriter.string(hook), false);
        appendField(result, "method", JsonWriter.string(method), false);
        appendField(result, "url", JsonWriter.string(url), false);
        appendField(result, "requestHeaders", JsonWriter.object(requestHeaders), false);
        appendField(result, "requestBody", bodyJson(requestBody), false);
        appendField(result, "responseCode", JsonWriter.number(responseCode), false);
        appendField(result, "responseMessage", JsonWriter.string(responseMessage), false);
        appendField(result, "responseHeaders", JsonWriter.object(responseHeaders), false);
        appendField(result, "responseBody", bodyJson(responseBody), false);
        appendField(result, "durationMs", JsonWriter.number(durationMs), false);
        appendField(result, "error", JsonWriter.string(error), false);
        result.append('}');
        return result.toString();
    }

    public String getId() {
        return id;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getHook() {
        return hook;
    }

    public String getUrl() {
        return url;
    }

    private static Map<String, String> copyHeaders(LinkedHashMap<String, String> headers) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String bodyJson(CaptureBody body) {
        if (body == null) {
            return "null";
        }
        return body.toJson();
    }

    private static void appendField(
            StringBuilder result,
            String name,
            String value,
            boolean first
    ) {
        if (!first) {
            result.append(',');
        }
        result.append(JsonWriter.string(name));
        result.append(':');
        result.append(value);
    }
}
