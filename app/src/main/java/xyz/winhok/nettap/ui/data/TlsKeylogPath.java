package xyz.winhok.nettap.ui.data;

import xyz.winhok.nettap.CaptureConfig;

public final class TlsKeylogPath {
    private static final String PLACEHOLDER = "<host-package>";

    private TlsKeylogPath() {
    }

    public static String template() {
        return forPackage(PLACEHOLDER);
    }

    public static String forPackage(String packageName) {
        String safePackage = packageName == null || packageName.trim().isEmpty()
                ? PLACEHOLDER
                : packageName.trim();
        return "/data/data/" + safePackage + "/files/" + CaptureConfig.TLS_KEYLOG_FILENAME;
    }
}
