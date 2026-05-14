package xyz.winhok.nettap.ui;

public final class CaptureFilterSummaryFormatter {
    private CaptureFilterSummaryFormatter() {
    }

    public static String format(
            String base,
            String host,
            String hook,
            String packageName,
            String query
    ) {
        StringBuilder value = new StringBuilder(base == null ? "" : base);
        append(value, "q", query);
        append(value, "host", host);
        append(value, "hook", hook);
        append(value, "package", packageName);
        return value.toString();
    }

    private static void append(StringBuilder value, String label, String rawText) {
        String text = normalize(rawText);
        if (text != null) {
            value.append(" | ").append(label).append("=").append(text);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
