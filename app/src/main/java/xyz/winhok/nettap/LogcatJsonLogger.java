package xyz.winhok.nettap;

import java.util.ArrayList;
import java.util.List;

public final class LogcatJsonLogger {
    private LogcatJsonLogger() {
    }

    public static List<String> chunks(String value, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than zero");
        }

        String safeValue = value == null ? "" : value;
        if (safeValue.isEmpty()) {
            List<String> result = new ArrayList<>();
            result.add("");
            return result;
        }

        List<String> result = new ArrayList<>();
        for (int start = 0; start < safeValue.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, safeValue.length());
            result.add(safeValue.substring(start, end));
        }
        return result;
    }

    public static List<String> utf8Chunks(String value, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be greater than zero");
        }

        String safeValue = value == null ? "" : value;
        if (safeValue.isEmpty()) {
            List<String> result = new ArrayList<>();
            result.add("");
            return result;
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (int index = 0; index < safeValue.length(); ) {
            int codePoint = safeValue.codePointAt(index);
            int nextBytes = utf8ByteLength(codePoint);
            if (nextBytes > maxBytes) {
                throw new IllegalArgumentException("maxBytes is smaller than a single UTF-8 code point");
            }

            if (currentBytes > 0 && currentBytes + nextBytes > maxBytes) {
                result.add(current.toString());
                current = new StringBuilder();
                currentBytes = 0;
            }

            current.appendCodePoint(codePoint);
            currentBytes += nextBytes;
            index += Character.charCount(codePoint);
        }

        result.add(current.toString());
        return result;
    }

    /** Arithmetic UTF-8 byte count for a single code point. */
    private static int utf8ByteLength(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    public static boolean isOversized(String json) {
        return json != null && json.length() > CaptureConfig.LOGCAT_MAX_JSON_CHARS;
    }

    public static void emit(String id, String json, XposedLogger logger) {
        if (logger == null) {
            return;
        }
        if (isOversized(json)) {
            logger.log(
                    "CAPTURE_JSON_OMITTED id=%s size=%d reason=logcat-size-gate",
                    id, json.length());
            return;
        }
        emitChunksAlways(id, json, logger);
    }

    public static void emitChunksAlways(String id, String json, XposedLogger logger) {
        if (logger == null) {
            return;
        }
        List<String> parts = utf8Chunks(json, CaptureConfig.LOGCAT_CHUNK_SIZE);
        int total = parts.size();
        for (int index = 0; index < total; index++) {
            logger.log("CAPTURE_JSON part=%d/%d id=%s chunk=%s", index + 1, total, id, parts.get(index));
        }
    }
}
