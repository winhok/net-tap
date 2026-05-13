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
            NetTap.getXposedLogger().logSafe("failed to serialize capture event: %s", e);
            return;
        }

        boolean oversized = LogcatJsonLogger.isOversized(json);
        FileLogger.WriteResult writeResult = FileLogger.WriteResult.UNAVAILABLE;
        try {
            writeResult = oversized
                    ? CAPTURE_FILE_LOGGER.logRawLineBlocking(json)
                    : CAPTURE_FILE_LOGGER.logRawLine(json);
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("failed to write capture event: %s", e);
        }

        try {
            if (writeResult.requiresLogcatFallback(oversized)) {
                LogcatJsonLogger.emitChunksAlways(event.getId(), json, NetTap.getXposedLogger());
            } else {
                LogcatJsonLogger.emit(event.getId(), json, NetTap.getXposedLogger());
            }
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("failed to emit capture event to logcat: %s", e);
        }
    }
}
