package xyz.winhok.nettap;

import java.nio.charset.StandardCharsets;

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

    /**
     * Decode {@code bytes} as UTF-8 text without consulting {@link BodyCapturePolicy}.
     * Null/empty bytes and decode failures turn into {@link #omitted} bodies whose
     * reason string is supplied by the caller so logs stay greppable.
     */
    public static CaptureBody fromBytes(
            byte[] bytes,
            long contentLength,
            boolean truncated,
            String emptyReason
    ) {
        if (bytes == null || bytes.length == 0) {
            return omitted(null, contentLength, null, emptyReason);
        }
        try {
            return text(null, contentLength, null, truncated,
                    new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return omitted(null, contentLength, null, "decode failed");
        }
    }

    /**
     * Decode a Cronet response body accumulator's bytes under the standard
     * {@link BodyCapturePolicy} rules, emitting the Cronet-specific
     * omitted-reason strings used across hook and log output.
     */
    public static CaptureBody fromCronetResponseBytes(
            byte[] bytes,
            String contentType,
            long contentLength,
            String encoding,
            boolean truncated
    ) {
        if (bytes == null || bytes.length == 0) {
            return omitted(contentType, contentLength, encoding,
                    "Cronet response body empty");
        }
        BodyCapturePolicy.Decision decision = BodyCapturePolicy.classify(contentType);
        if (decision == BodyCapturePolicy.Decision.BINARY) {
            return omitted(contentType, contentLength, encoding,
                    "Cronet response body content-type is binary");
        }
        if (decision == BodyCapturePolicy.Decision.UNKNOWN) {
            return omitted(contentType, contentLength, encoding,
                    "Cronet response body content-type unsupported");
        }
        return text(contentType, contentLength, encoding, truncated,
                new String(bytes, StandardCharsets.UTF_8));
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
