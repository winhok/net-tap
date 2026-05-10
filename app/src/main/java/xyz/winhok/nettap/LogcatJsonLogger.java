package xyz.winhok.nettap;

import java.nio.charset.StandardCharsets;
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
        if (safeValue.length() == 0) {
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
        if (safeValue.length() == 0) {
            List<String> result = new ArrayList<>();
            result.add("");
            return result;
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (int index = 0; index < safeValue.length(); ) {
            int codePoint = safeValue.codePointAt(index);
            String next = new String(Character.toChars(codePoint));
            int nextBytes = next.getBytes(StandardCharsets.UTF_8).length;
            if (nextBytes > maxBytes) {
                throw new IllegalArgumentException("maxBytes is smaller than a single UTF-8 code point");
            }

            if (currentBytes > 0 && currentBytes + nextBytes > maxBytes) {
                result.add(current.toString());
                current = new StringBuilder();
                currentBytes = 0;
            }

            current.append(next);
            currentBytes += nextBytes;
            index += Character.charCount(codePoint);
        }

        result.add(current.toString());
        return result;
    }

    public static void emit(String id, String json, XposedLogger logger) {
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
