package xyz.winhok.nettap.ui;

import xyz.winhok.nettap.RuntimeCaptureConfig;

public final class UiPreferenceSanitizer {
    private UiPreferenceSanitizer() {
    }

    public static int port(int value) {
        if (value < 1 || value > 65535) {
            return RuntimeCaptureConfig.DEFAULT_REALTIME_PORT;
        }
        return value;
    }

    public static int positiveLimit(int value) {
        return Math.max(1, value);
    }
}
