package xyz.winhok.nettap;

public final class CaptureRecorder {
    private static final FileLogger CAPTURE_FILE_LOGGER = new FileLogger(
            CaptureConfig.CAPTURE_FILENAME,
            true
    );

    private CaptureRecorder() {
    }

    public static void record(CaptureEvent event) {
        if (event == null) {
            return;
        }

        String json;
        try {
            json = event.toJson();
        } catch (Throwable e) {
            logFailure("failed to serialize capture event: %s", e);
            return;
        }

        try {
            CAPTURE_FILE_LOGGER.logRawLine(json);
        } catch (Throwable e) {
            logFailure("failed to write capture event: %s", e);
        }

        try {
            LogcatJsonLogger.emit(event.getId(), json, NetTap.getXposedLogger());
        } catch (Throwable e) {
            logFailure("failed to emit capture event to logcat: %s", e);
        }
    }

    private static void logFailure(String message, Throwable throwable) {
        try {
            NetTap.getXposedLogger().log(message, throwable);
        } catch (Throwable ignored) {
        }
    }
}
