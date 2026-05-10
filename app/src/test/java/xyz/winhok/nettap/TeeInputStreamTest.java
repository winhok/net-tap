package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class TeeInputStreamTest {

    @Test
    public void readAllCapturesAndFiresOnce() throws Exception {
        byte[] payload = "hello world".getBytes();
        AtomicInteger fires = new AtomicInteger();
        TeeInputStream tee = new TeeInputStream(
                new ByteArrayInputStream(payload),
                100,
                (bytes, truncated, total) -> fires.incrementAndGet());
        byte[] buf = new byte[32];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = tee.read(buf, 0, buf.length)) != -1) {
            sb.append(new String(buf, 0, n));
        }
        assertEquals("hello world", sb.toString());
        assertEquals(new String(payload), new String(tee.capturedBytes()));
        tee.close();
        assertEquals(1, fires.get());
    }

    @Test
    public void truncatesCaptureAtCap() throws Exception {
        byte[] payload = new byte[200];
        for (int i = 0; i < 200; i++) {
            payload[i] = (byte) i;
        }
        TeeInputStream tee = new TeeInputStream(
                new ByteArrayInputStream(payload), 50, null);
        byte[] buf = new byte[300];
        int total = 0;
        int n;
        while ((n = tee.read(buf, total, buf.length - total)) != -1) {
            total += n;
        }
        assertEquals(200, total);
        assertEquals(50, tee.capturedBytes().length);
        assertTrue(tee.isTruncated());
        assertEquals(200L, tee.totalObserved());
    }

    @Test
    public void closeFiresEndCallbackWithoutEof() throws Exception {
        byte[] payload = "abcdefgh".getBytes();
        AtomicInteger fires = new AtomicInteger();
        TeeInputStream tee = new TeeInputStream(
                new ByteArrayInputStream(payload),
                100,
                (bytes, truncated, total) -> fires.incrementAndGet());
        byte[] buf = new byte[2];
        tee.read(buf, 0, 2);
        tee.close();
        assertEquals(1, fires.get());
    }

    @Test
    public void singleByteReadPathObservesCap() throws Exception {
        TeeInputStream tee = new TeeInputStream(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 2, null);
        assertEquals(1, tee.read());
        assertEquals(2, tee.read());
        assertEquals(3, tee.read());
        assertEquals(-1, tee.read());
        assertEquals(2, tee.capturedBytes().length);
        assertTrue(tee.isTruncated());
    }
}
