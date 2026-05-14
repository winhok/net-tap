package xyz.winhok.nettap.ui;

public final class RealtimeStatusFormatter {
    private RealtimeStatusFormatter() {
    }

    public static String format(int port, String error, boolean enabled) {
        if (!enabled) {
            return "Realtime transport disabled";
        }
        String endpoint = "127.0.0.1:" + port;
        if (error == null || error.trim().isEmpty()) {
            return "Listening on " + endpoint;
        }
        return "Realtime unavailable on " + endpoint + ": " + error;
    }
}
