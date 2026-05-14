package xyz.winhok.nettap.ui.data;

import java.util.Map;

public final class CaptureBodySnapshot {
    private final String contentType;
    private final long contentLength;
    private final String encoding;
    private final boolean truncated;
    private final String text;
    private final String omittedReason;

    private CaptureBodySnapshot(
            String contentType,
            long contentLength,
            String encoding,
            boolean truncated,
            String text,
            String omittedReason
    ) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.encoding = encoding;
        this.truncated = truncated;
        this.text = text;
        this.omittedReason = omittedReason;
    }

    static CaptureBodySnapshot fromValue(Object value) {
        Map<String, Object> map = SimpleJsonParser.castMap(value);
        return new CaptureBodySnapshot(
                CaptureUiEvent.stringValue(map.get("contentType")),
                CaptureUiEvent.longValue(map.get("contentLength"), -1L),
                CaptureUiEvent.stringValue(map.get("encoding")),
                CaptureUiEvent.booleanValue(map.get("truncated"), false),
                CaptureUiEvent.stringValue(map.get("text")),
                CaptureUiEvent.stringValue(map.get("omittedReason"))
        );
    }

    public static CaptureBodySnapshot text(
            String contentType,
            long contentLength,
            String encoding,
            boolean truncated,
            String text
    ) {
        return new CaptureBodySnapshot(contentType, contentLength, encoding, truncated, text, null);
    }

    public static CaptureBodySnapshot omitted(
            String contentType,
            long contentLength,
            String encoding,
            String omittedReason
    ) {
        return new CaptureBodySnapshot(contentType, contentLength, encoding, false, null, omittedReason);
    }

    public static CaptureBodySnapshot empty() {
        return new CaptureBodySnapshot(null, -1L, null, false, null, null);
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getEncoding() {
        return encoding;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getText() {
        return text;
    }

    public String getOmittedReason() {
        return omittedReason;
    }

    long approximateBytes() {
        long total = 0L;
        total += size(contentType);
        total += size(encoding);
        total += size(text);
        total += size(omittedReason);
        return Math.max(total, contentLength > 0 ? Math.min(contentLength, Integer.MAX_VALUE) : 0L);
    }

    private static long size(String value) {
        return value == null ? 0L : value.length() * 2L;
    }
}
