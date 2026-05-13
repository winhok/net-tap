package xyz.winhok.nettap;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FileLoggerWriteResultTest {
    @Test
    public void oversizedJsonForcesLogcatUnlessWrittenSynchronously() {
        assertFalse(FileLogger.WriteResult.WRITTEN_SYNC.requiresLogcatFallback(true));
        assertTrue(FileLogger.WriteResult.ENQUEUED.requiresLogcatFallback(true));
        assertTrue(FileLogger.WriteResult.UNAVAILABLE.requiresLogcatFallback(true));
        assertTrue(FileLogger.WriteResult.DROPPED.requiresLogcatFallback(true));
    }

    @Test
    public void smallJsonOnlyForcesLogcatWhenFileWriteFails() {
        assertFalse(FileLogger.WriteResult.ENQUEUED.requiresLogcatFallback(false));
        assertFalse(FileLogger.WriteResult.WRITTEN_SYNC.requiresLogcatFallback(false));
        assertTrue(FileLogger.WriteResult.UNAVAILABLE.requiresLogcatFallback(false));
        assertTrue(FileLogger.WriteResult.DROPPED.requiresLogcatFallback(false));
    }
}
