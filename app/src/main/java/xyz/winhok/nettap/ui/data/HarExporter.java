package xyz.winhok.nettap.ui.data;

import java.util.List;
import java.util.Map;

import xyz.winhok.nettap.JsonWriter;

public final class HarExporter {
    private HarExporter() {
    }

    public static String export(List<CaptureUiEvent> events, boolean partial) {
        return build(events, partial).getJson();
    }

    static ExportedHar build(List<CaptureUiEvent> events, boolean partial) {
        EntryExport entries = entries(events);
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "log", log(entries, partial), true);
        json.append('}');
        return new ExportedHar(JsonFormatter.formatTrusted(json.toString()), entries.exportedCount);
    }

    private static String log(EntryExport entries, boolean partial) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "version", JsonWriter.string("1.2"), true);
        field(json, "creator", creator(), false);
        field(json, "entries", entries.json, false);
        field(json, "_nettap", nettapMeta(partial, entries.skippedCount), false);
        json.append('}');
        return json.toString();
    }

    private static String creator() {
        return "{\"name\":\"Net-Tap\",\"version\":\"1.0\"}";
    }

    private static String nettapMeta(boolean partial, int skippedEntries) {
        return "{\"partial\":"
                + JsonWriter.bool(partial)
                + ",\"skippedEntries\":"
                + skippedEntries
                + "}";
    }

    private static EntryExport entries(List<CaptureUiEvent> events) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        boolean first = true;
        int skippedCount = 0;
        int exportedCount = 0;
        if (events != null) {
            for (CaptureUiEvent event : events) {
                if (event == null) {
                    skippedCount++;
                    continue;
                }
                String entryJson;
                try {
                    entryJson = entry(event);
                } catch (RuntimeException e) {
                    skippedCount++;
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append(entryJson);
                exportedCount++;
            }
        }
        json.append(']');
        return new EntryExport(json.toString(), skippedCount, exportedCount);
    }

    private static String entry(CaptureUiEvent event) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "startedDateTime", JsonWriter.string(event.getTimestamp()), true);
        field(json, "time", Long.toString(event.getDurationMs()), false);
        field(json, "request", request(event), false);
        field(json, "response", response(event), false);
        field(json, "cache", "{}", false);
        field(json, "timings", timings(event), false);
        field(json, "_nettap", eventMeta(event), false);
        json.append('}');
        return json.toString();
    }

    private static String request(CaptureUiEvent event) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "method", JsonWriter.string(event.getMethod()), true);
        field(json, "url", JsonWriter.string(event.getUrl()), false);
        field(json, "httpVersion", JsonWriter.string("HTTP/1.1"), false);
        field(json, "headers", headers(event.getRequestHeaders()), false);
        field(json, "queryString", queryString(event.getQuery()), false);
        field(json, "cookies", cookies(CookieParser.parse(event), CookieEntry.Source.REQUEST), false);
        field(json, "headersSize", "-1", false);
        field(json, "bodySize", "-1", false);
        field(json, "postData", postData(event), false);
        json.append('}');
        return json.toString();
    }

    private static String response(CaptureUiEvent event) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "status", Integer.toString(event.getResponseCode()), true);
        field(json, "statusText", JsonWriter.string(event.getResponseMessage()), false);
        field(json, "httpVersion", JsonWriter.string("HTTP/1.1"), false);
        field(json, "headers", headers(event.getResponseHeaders()), false);
        field(json, "cookies", cookies(CookieParser.parse(event), CookieEntry.Source.RESPONSE), false);
        field(json, "content", content(event), false);
        field(json, "redirectURL", JsonWriter.string(headerValue(
                event.getResponseHeaders(),
                "Location"
        )), false);
        field(json, "headersSize", "-1", false);
        field(json, "bodySize", "-1", false);
        json.append('}');
        return json.toString();
    }

    private static String timings(CaptureUiEvent event) {
        return "{\"send\":0,\"wait\":"
                + event.getDurationMs()
                + ",\"receive\":0,\"_nettap\":{\"note\":\"aggregate\"}}";
    }

    private static String headers(Map<String, String> headers) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        boolean first = true;
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "name", JsonWriter.string(header.getKey()), true);
            field(json, "value", JsonWriter.string(header.getValue()), false);
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return "";
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (name.equalsIgnoreCase(header.getKey())) {
                return header.getValue() == null ? "" : header.getValue();
            }
        }
        return "";
    }

    private static String queryString(String query) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        List<QueryParam> params = QueryParamParser.parse(query);
        if (!params.isEmpty()) {
            boolean first = true;
            for (QueryParam param : params) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('{');
                field(json, "name", JsonWriter.string(param.getName()), true);
                field(json, "value", JsonWriter.string(param.getValue()), false);
                json.append('}');
            }
        }
        json.append(']');
        return json.toString();
    }

    private static String cookies(List<CookieEntry> cookies, CookieEntry.Source source) {
        StringBuilder json = new StringBuilder();
        json.append('[');
        boolean first = true;
        for (CookieEntry cookie : cookies) {
            if (cookie.getSource() != source) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "name", JsonWriter.string(cookie.getName()), true);
            field(json, "value", JsonWriter.string(cookie.getValue()), false);
            field(json, "secure", JsonWriter.bool(cookie.isSecure()), false);
            field(json, "httpOnly", JsonWriter.bool(cookie.isHttpOnly()), false);
            if (cookie.getSameSite() != null) {
                field(json, "sameSite", JsonWriter.string(cookie.getSameSite()), false);
            }
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private static String postData(CaptureUiEvent event) {
        CaptureBodySnapshot body = event.getRequestBody();
        String text = body.getText();
        if (text == null) {
            text = "";
        }
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "mimeType", JsonWriter.string(mimeType(
                event.getRequestHeaders(),
                body
        )), true);
        field(json, "text", JsonWriter.string(text), false);
        json.append('}');
        return json.toString();
    }

    private static String content(CaptureUiEvent event) {
        CaptureBodySnapshot body = event.getResponseBody();
        String text = body.getText();
        if (text == null) {
            text = "";
        }
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "size", Long.toString(bodySize(body)), true);
        field(json, "mimeType", JsonWriter.string(mimeType(
                event.getResponseHeaders(),
                body
        )), false);
        field(json, "text", JsonWriter.string(text), false);
        if (body.isTruncated()) {
            field(json, "_nettapTruncated", "true", false);
        }
        if (body.getOmittedReason() != null) {
            field(json, "_nettapOmittedReason", JsonWriter.string(body.getOmittedReason()), false);
        }
        json.append('}');
        return json.toString();
    }

    private static String mimeType(Map<String, String> headers, CaptureBodySnapshot body) {
        String header = headerValue(headers, "Content-Type");
        if (!header.isEmpty()) {
            return header;
        }
        String contentType = body.getContentType();
        return contentType == null ? "" : contentType;
    }

    private static long bodySize(CaptureBodySnapshot body) {
        if (body.getContentLength() >= 0) {
            return body.getContentLength();
        }
        return -1L;
    }

    private static String eventMeta(CaptureUiEvent event) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "id", JsonWriter.string(event.getId()), true);
        field(json, "packageName", JsonWriter.string(event.getPackageName()), false);
        field(json, "hook", JsonWriter.string(event.getHook()), false);
        field(json, "error", JsonWriter.string(event.getError()), false);
        field(json, "requestTruncated", JsonWriter.bool(event.getRequestBody().isTruncated()), false);
        field(json, "responseTruncated", JsonWriter.bool(event.getResponseBody().isTruncated()), false);
        json.append('}');
        return json.toString();
    }

    private static void field(StringBuilder json, String name, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append(JsonWriter.string(name)).append(':').append(value);
    }

    private static final class EntryExport {
        private final String json;
        private final int skippedCount;
        private final int exportedCount;

        private EntryExport(String json, int skippedCount, int exportedCount) {
            this.json = json;
            this.skippedCount = skippedCount;
            this.exportedCount = exportedCount;
        }
    }

    static final class ExportedHar {
        private final String json;
        private final int entryCount;

        private ExportedHar(String json, int entryCount) {
            this.json = json;
            this.entryCount = entryCount;
        }

        String getJson() {
            return json;
        }

        int getEntryCount() {
            return entryCount;
        }
    }
}
