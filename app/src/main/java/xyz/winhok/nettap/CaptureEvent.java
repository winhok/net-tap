package xyz.winhok.nettap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CaptureEvent {
    private final String id;
    private final String timestamp;
    private final String packageName;
    private final String okhttpHook;
    private final String method;
    private final String url;
    private final Map<String, String> requestHeaders;
    private final CaptureBody requestBody;
    private final long responseCode;
    private final String responseMessage;
    private final Map<String, String> responseHeaders;
    private final CaptureBody responseBody;
    private final long durationMs;
    private final String error;

    private CaptureEvent(
            String id,
            String timestamp,
            String packageName,
            String okhttpHook,
            String method,
            String url,
            LinkedHashMap<String, String> requestHeaders,
            CaptureBody requestBody,
            long responseCode,
            String responseMessage,
            LinkedHashMap<String, String> responseHeaders,
            CaptureBody responseBody,
            long durationMs,
            String error
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.packageName = packageName;
        this.okhttpHook = okhttpHook;
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

    public static CaptureEvent complete(
            String id,
            String timestamp,
            String packageName,
            String okhttpHook,
            String method,
            String url,
            LinkedHashMap<String, String> requestHeaders,
            CaptureBody requestBody,
            long responseCode,
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
                okhttpHook,
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

    public String toJson() {
        StringBuilder result = new StringBuilder();
        result.append('{');
        appendField(result, "id", JsonWriter.string(id), true);
        appendField(result, "timestamp", JsonWriter.string(timestamp), false);
        appendField(result, "packageName", JsonWriter.string(packageName), false);
        appendField(result, "okhttpHook", JsonWriter.string(okhttpHook), false);
        appendField(result, "method", JsonWriter.string(method), false);
        appendField(result, "url", JsonWriter.string(url), false);
        appendField(result, "requestHeaders", JsonWriter.object(new LinkedHashMap<>(requestHeaders)), false);
        appendField(result, "requestBody", bodyJson(requestBody), false);
        appendField(result, "responseCode", JsonWriter.number(responseCode), false);
        appendField(result, "responseMessage", JsonWriter.string(responseMessage), false);
        appendField(result, "responseHeaders", JsonWriter.object(new LinkedHashMap<>(responseHeaders)), false);
        appendField(result, "responseBody", bodyJson(responseBody), false);
        appendField(result, "durationMs", JsonWriter.number(durationMs), false);
        appendField(result, "error", JsonWriter.string(error), false);
        result.append('}');
        return result.toString();
    }

    public String getId() {
        return id;
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
