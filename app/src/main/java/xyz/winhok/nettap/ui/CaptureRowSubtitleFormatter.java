package xyz.winhok.nettap.ui;

import java.util.Locale;

import xyz.winhok.nettap.ui.data.CaptureBodySnapshot;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

public final class CaptureRowSubtitleFormatter {
    private CaptureRowSubtitleFormatter() {
    }

    public static String format(int index, CaptureUiEvent event) {
        if (event == null) {
            return "";
        }
        return "#" + (index + 1)
                + " - " + shortTime(event.getTimestamp())
                + " - " + bodyKind(event.getResponseBody())
                + " - " + formatBytes(event.getApproximateBytes())
                + " - " + event.getDurationMs() + "ms";
    }

    private static String shortTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "--:--:--";
        }
        int timeStart = timestamp.indexOf('T');
        String value = timeStart >= 0 && timeStart + 9 <= timestamp.length()
                ? timestamp.substring(timeStart + 1, timeStart + 9)
                : timestamp;
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private static String bodyKind(CaptureBodySnapshot body) {
        String rawContentType = body == null ? null : body.getContentType();
        if (rawContentType == null || rawContentType.isEmpty()) {
            return "BODY";
        }
        String contentType = rawContentType.toLowerCase(Locale.US);
        if (contentType.contains("json")) {
            return "JSON";
        }
        if (contentType.contains("html")) {
            return "HTML";
        }
        if (contentType.contains("xml")) {
            return "XML";
        }
        if (contentType.startsWith("text/")) {
            return "TEXT";
        }
        return "BODY";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + "B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1fKB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
