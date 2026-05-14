package xyz.winhok.nettap.ui.data;

import java.nio.charset.StandardCharsets;

public final class BodyDisplayState {
    private static final long LARGE_BODY_THRESHOLD_BYTES = 2L * 1024L * 1024L;

    private final String status;
    private final String text;
    private final String language;
    private final long contentLength;
    private final boolean requiresLargeBodyConfirmation;
    private final boolean binaryBase64;

    private BodyDisplayState(
            String status,
            String text,
            String language,
            long contentLength,
            boolean requiresLargeBodyConfirmation,
            boolean binaryBase64
    ) {
        this.status = status;
        this.text = text == null ? "" : text;
        this.language = language == null ? "text" : language;
        this.contentLength = contentLength;
        this.requiresLargeBodyConfirmation = requiresLargeBodyConfirmation;
        this.binaryBase64 = binaryBase64;
    }

    public static BodyDisplayState empty() {
        return new BodyDisplayState("No body selected", "", "text", -1L, false, false);
    }

    public static BodyDisplayState from(CaptureBodySnapshot body) {
        if (body == null) {
            return empty();
        }
        String language = detectLanguage(body.getContentType(), body.getText());
        boolean requiresConfirmation = requiresConfirmation(body);
        if (body.getOmittedReason() != null && !body.getOmittedReason().isEmpty()) {
            return new BodyDisplayState(
                    "Omitted: " + body.getOmittedReason(),
                    "",
                    language,
                    body.getContentLength(),
                    requiresConfirmation,
                    false
            );
        }
        String raw = body.getText();
        if (raw == null || raw.isEmpty()) {
            return new BodyDisplayState("OK", "", language, body.getContentLength(), requiresConfirmation, false);
        }
        if (isBinaryContentType(body.getContentType())) {
            return new BodyDisplayState(
                    "Binary (base64)",
                    raw,
                    language,
                    body.getContentLength(),
                    requiresConfirmation,
                    true
            );
        }
        String text = raw;
        String status = body.isTruncated() ? truncationStatus(raw, body.getContentLength()) : "OK";
        if (requiresConfirmation) {
            return new BodyDisplayState(status, text, language, body.getContentLength(), true, false);
        }
        if (JsonFormatter.isJsonCandidate(raw) || isJsonContentType(body.getContentType())) {
            try {
                text = JsonFormatter.format(raw);
            } catch (IllegalArgumentException e) {
                status = "Format failed: raw shown";
                text = raw;
            }
        }
        return new BodyDisplayState(status, text, language, body.getContentLength(), requiresConfirmation, false);
    }

    private static boolean isJsonContentType(String contentType) {
        return contentType != null && contentType.toLowerCase(java.util.Locale.US).contains("json");
    }

    private static boolean isBinaryContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }
        String lower = contentType.toLowerCase(java.util.Locale.US);
        return lower.startsWith("image/")
                || lower.startsWith("audio/")
                || lower.startsWith("video/")
                || lower.contains("octet-stream")
                || lower.contains("protobuf");
    }

    private static String detectLanguage(String contentType, String text) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(java.util.Locale.US);
            if (lower.contains("json")) {
                return "json";
            }
            if (lower.contains("html")) {
                return "html";
            }
            if (lower.contains("xml")) {
                return "xml";
            }
        }
        if (text != null) {
            String trimmed = text.trim().toLowerCase(java.util.Locale.US);
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return "json";
            }
            if (trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")) {
                return "html";
            }
            if (trimmed.startsWith("<")) {
                return "xml";
            }
        }
        return "text";
    }

    private static boolean requiresConfirmation(CaptureBodySnapshot body) {
        long contentLength = body.getContentLength();
        String text = body.getText();
        long textBytes = utf8Bytes(text);
        return Math.max(contentLength, textBytes) > LARGE_BODY_THRESHOLD_BYTES;
    }

    private static String truncationStatus(String text, long contentLength) {
        long shownBytes = utf8Bytes(text);
        if (contentLength > 0L) {
            return "Truncated: " + shownBytes + " / " + contentLength + " bytes";
        }
        return "Truncated: " + shownBytes + " bytes";
    }

    private static long utf8Bytes(String text) {
        return text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
    }

    public String getStatus() {
        return status;
    }

    public String getText() {
        return text;
    }

    public String getLanguage() {
        return language;
    }

    public long getContentLength() {
        return contentLength;
    }

    public boolean requiresLargeBodyConfirmation() {
        return requiresLargeBodyConfirmation;
    }

    public boolean isBinaryBase64() {
        return binaryBase64;
    }
}
