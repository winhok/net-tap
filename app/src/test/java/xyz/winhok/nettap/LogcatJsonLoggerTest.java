package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class LogcatJsonLoggerTest {
    @Test
    public void chunksSplitsValueIntoSequentialParts() {
        assertEquals(Arrays.asList("ab", "cd", "ef"), LogcatJsonLogger.chunks("abcdef", 2));
    }

    @Test
    public void chunksTreatsNullAsEmptyString() {
        assertEquals(Arrays.asList(""), LogcatJsonLogger.chunks(null, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void chunksRejectsNonPositiveChunkSize() {
        LogcatJsonLogger.chunks("abcdef", 0);
    }

    @Test
    public void utf8ChunksPreservesCodePointBoundaries() {
        List<String> chunks = LogcatJsonLogger.utf8Chunks("a\uD83D\uDE00b", 5);

        assertEquals(Arrays.asList("a\uD83D\uDE00", "b"), chunks);
        for (String chunk : chunks) {
            assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 5);
        }
    }

    @Test
    public void emitLogsSelfIdentifyingChunk() {
        RecordingLogger logger = new RecordingLogger();

        LogcatJsonLogger.emit("request-1", "abcdef", logger);

        assertEquals(
                Arrays.asList(
                        "CAPTURE_JSON part=1/1 id=request-1 chunk=abcdef"
                ),
                logger.messages
        );
    }

    @Test
    public void emitLogsMultipleSelfIdentifyingUtf8Chunks() {
        RecordingLogger logger = new RecordingLogger();
        String firstChunk = repeat('a', CaptureConfig.LOGCAT_CHUNK_SIZE);
        String json = firstChunk + "b";

        LogcatJsonLogger.emit("request-2", json, logger);

        assertEquals(
                Arrays.asList(
                        "CAPTURE_JSON part=1/2 id=request-2 chunk=" + firstChunk,
                        "CAPTURE_JSON part=2/2 id=request-2 chunk=b"
                ),
                logger.messages
        );
    }

    @Test
    public void emitTreatsNullJsonAsEmptyString() {
        RecordingLogger logger = new RecordingLogger();

        LogcatJsonLogger.emit("request-1", null, logger);

        assertEquals(
                Arrays.asList("CAPTURE_JSON part=1/1 id=request-1 chunk="),
                logger.messages
        );
    }

    @Test
    public void emitOmitsOversizedJsonByDefault() {
        RecordingLogger logger = new RecordingLogger();
        String json = repeat('a', CaptureConfig.LOGCAT_MAX_JSON_CHARS + 1);

        LogcatJsonLogger.emit("request-large", json, logger);

        assertEquals(
                Arrays.asList(
                        "CAPTURE_JSON_OMITTED id=request-large size="
                                + (CaptureConfig.LOGCAT_MAX_JSON_CHARS + 1)
                                + " reason=logcat-size-gate"
                ),
                logger.messages
        );
    }

    @Test
    public void emitChunksOversizedJsonWhenForced() {
        RecordingLogger logger = new RecordingLogger();
        String json = repeat('a', CaptureConfig.LOGCAT_MAX_JSON_CHARS + 1);

        LogcatJsonLogger.emitChunksAlways("request-large", json, logger);

        assertTrue(logger.messages.size() > 1);
        assertTrue(logger.messages.get(0).startsWith("CAPTURE_JSON part=1/"));
    }

    @Test
    public void emitIgnoresNullLogger() {
        LogcatJsonLogger.emit("request-1", "abcdef", null);
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static final class RecordingLogger extends XposedLogger {
        private final List<String> messages = new ArrayList<>();

        private RecordingLogger() {
            super("test");
        }

        @Override
        public void log(String message, Object... objects) {
            if (objects.length > 0) {
                messages.add(String.format(message, objects));
                return;
            }
            messages.add(message);
        }
    }
}
