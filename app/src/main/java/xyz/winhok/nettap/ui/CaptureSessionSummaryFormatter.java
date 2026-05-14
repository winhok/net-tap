package xyz.winhok.nettap.ui;

import java.util.Locale;

import xyz.winhok.nettap.ui.data.CaptureSessionStore;

public final class CaptureSessionSummaryFormatter {
    private CaptureSessionSummaryFormatter() {
    }

    public static String format(int visibleCount, CaptureSessionStore store) {
        int total = store == null ? 0 : store.totalCount();
        long bytes = store == null ? 0L : store.approximateBytes();
        CaptureSessionStore.State state = store == null
                ? CaptureSessionStore.State.CLEARED
                : store.getState();
        return visibleCount + " / " + total + " shown · " + formatBytes(bytes)
                + " · " + state.name().toLowerCase(Locale.US);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024D;
        if (kib < 1024D) {
            return String.format(Locale.US, "%.1f KB", kib);
        }
        return String.format(Locale.US, "%.1f MB", kib / 1024D);
    }
}
