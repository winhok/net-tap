package xyz.winhok.nettap;

public final class CaptureBody {
    private final String contentType;
    private final long contentLength;
    private final String encoding;
    private final boolean truncated;
    private final String text;
    private final String omittedReason;

    private CaptureBody(
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

    public static CaptureBody text(
            String contentType,
            long contentLength,
            String encoding,
            boolean truncated,
            String text
    ) {
        return new CaptureBody(contentType, contentLength, encoding, truncated, text, null);
    }

    public static CaptureBody omitted(
            String contentType,
            long contentLength,
            String encoding,
            String omittedReason
    ) {
        return new CaptureBody(contentType, contentLength, encoding, false, null, omittedReason);
    }

    public String toJson() {
        StringBuilder result = new StringBuilder();
        result.append('{');
        appendField(result, "contentType", JsonWriter.string(contentType), true);
        appendField(result, "contentLength", JsonWriter.number(contentLength), false);
        appendField(result, "encoding", JsonWriter.string(encoding), false);
        appendField(result, "truncated", JsonWriter.bool(truncated), false);
        appendField(result, "text", JsonWriter.string(text), false);
        appendField(result, "omittedReason", JsonWriter.string(omittedReason), false);
        result.append('}');
        return result.toString();
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
