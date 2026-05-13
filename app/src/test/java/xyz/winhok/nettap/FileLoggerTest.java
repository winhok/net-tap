package xyz.winhok.nettap;

import android.content.Context;
import android.content.ContextWrapper;

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public final class FileLoggerTest {
    @Test
    public void logRawLineReportsMissingContextOnlyOnce() {
        RecordingFailureReporter reporter = new RecordingFailureReporter();
        FileLogger logger = new FileLogger(
                "_capture.jsonl",
                true,
                new FileLogger.ContextProvider() {
                    @Override
                    public Context currentContext() {
                        return null;
                    }
                },
                reporter
        );

        assertEquals(FileLogger.WriteResult.UNAVAILABLE, logger.logRawLine("{\"id\":1}"));
        assertEquals(FileLogger.WriteResult.UNAVAILABLE, logger.logRawLine("{\"id\":2}"));

        assertEquals(1, reporter.count);
        assertEquals("context unavailable", reporter.message);
    }

    @Test
    public void logRawLineEnqueuesWithValidContext() throws Exception {
        File file = File.createTempFile("nettap", ".jsonl");
        RecordingFailureReporter reporter = new RecordingFailureReporter();
        FileLogger logger = loggerForFile(file, reporter);

        assertEquals(FileLogger.WriteResult.ENQUEUED, logger.logRawLine("{\"id\":1}"));

        logger.closeQuietly();
        assertEquals(0, reporter.count);
        assertEquals("{\"id\":1}\n", Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void logRawLineBlockingWritesAndFlushesWithValidContext() throws Exception {
        File file = File.createTempFile("nettap", ".jsonl");
        FileLogger logger = loggerForFile(file, new RecordingFailureReporter());

        assertEquals(FileLogger.WriteResult.WRITTEN_SYNC, logger.logRawLineBlocking("{\"id\":1}"));

        assertEquals("{\"id\":1}\n", Files.readString(file.toPath(), StandardCharsets.UTF_8));
        logger.closeQuietly();
    }

    @Test
    public void logRawLineBlockingPreservesQueuedOrder() throws Exception {
        File file = File.createTempFile("nettap", ".jsonl");
        FileLogger logger = loggerForFile(file, new RecordingFailureReporter());

        assertEquals(FileLogger.WriteResult.ENQUEUED, logger.logRawLine("{\"id\":1}"));
        assertEquals(FileLogger.WriteResult.WRITTEN_SYNC, logger.logRawLineBlocking("{\"id\":2}"));

        logger.closeQuietly();
        assertEquals(
                "{\"id\":1}\n{\"id\":2}\n",
                Files.readString(file.toPath(), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void nullFailureReporterDoesNotThrowWhenContextMissing() {
        FileLogger logger = new FileLogger(
                "_capture.jsonl",
                true,
                new FileLogger.ContextProvider() {
                    @Override
                    public Context currentContext() {
                        return null;
                    }
                },
                null
        );

        assertEquals(FileLogger.WriteResult.UNAVAILABLE, logger.logRawLine("{\"id\":1}"));
    }

    @Test
    public void syncWriteFailureMarksLoggerUnavailable() throws Exception {
        File file = File.createTempFile("nettap", ".jsonl");
        RecordingFailureReporter reporter = new RecordingFailureReporter();
        FileLogger logger = new FileLogger(
                "_capture.jsonl",
                true,
                contextProvider(new ThrowingContext(file)),
                reporter
        );

        assertEquals(FileLogger.WriteResult.UNAVAILABLE, logger.logRawLineBlocking("{\"id\":1}"));
        assertEquals(FileLogger.WriteResult.UNAVAILABLE, logger.logRawLine("{\"id\":2}"));
        assertEquals(1, reporter.count);
        assertEquals("write failed", reporter.message);
    }

    private static FileLogger loggerForFile(File file, RecordingFailureReporter reporter) {
        return new FileLogger(
                "_capture.jsonl",
                true,
                contextProvider(new FileBackedContext(file)),
                reporter
        );
    }

    private static FileLogger.ContextProvider contextProvider(final Context context) {
        return new FileLogger.ContextProvider() {
            @Override
            public Context currentContext() {
                return context;
            }
        };
    }

    private static final class RecordingFailureReporter implements FileLogger.FailureReporter {
        int count;
        String message;

        @Override
        public void report(String message, Throwable throwable) {
            count++;
            this.message = message;
        }
    }

    private static class FileBackedContext extends ContextWrapper {
        private final File file;

        FileBackedContext(File file) {
            super(null);
            this.file = file;
        }

        @Override
        public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
            return new FileOutputStream(file, true);
        }
    }

    private static final class ThrowingContext extends FileBackedContext {
        private final File file;

        ThrowingContext(File file) {
            super(file);
            this.file = file;
        }

        @Override
        public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
            return new ThrowingFileOutputStream(file);
        }
    }

    private static final class ThrowingFileOutputStream extends FileOutputStream {
        ThrowingFileOutputStream(File file) throws FileNotFoundException {
            super(file);
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            throw new IOException("simulated write failure");
        }

        @Override
        public void write(int oneByte) throws IOException {
            throw new IOException("simulated write failure");
        }
    }
}
