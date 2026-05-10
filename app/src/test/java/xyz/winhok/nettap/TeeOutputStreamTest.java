package xyz.winhok.nettap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

public final class TeeOutputStreamTest {

    @Test
    public void capturesAllWritesUnderCap() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        TeeOutputStream tee = new TeeOutputStream(sink, 100);
        byte[] payload = "hello".getBytes();
        tee.write(payload);
        tee.close();
        assertArrayEquals(payload, sink.toByteArray());
        assertArrayEquals(payload, tee.capturedBytes());
        assertFalse(tee.isTruncated());
        assertEquals(payload.length, tee.totalObserved());
    }

    @Test
    public void truncatesCaptureButStillWritesAll() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        TeeOutputStream tee = new TeeOutputStream(sink, 4);
        byte[] payload = "hello world".getBytes();
        tee.write(payload);
        tee.close();
        assertArrayEquals(payload, sink.toByteArray());
        assertEquals(4, tee.capturedBytes().length);
        assertTrue(tee.isTruncated());
        assertEquals(payload.length, tee.totalObserved());
    }

    @Test
    public void writeSingleByteRespectsCap() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        TeeOutputStream tee = new TeeOutputStream(sink, 2);
        tee.write((byte) 'a');
        tee.write((byte) 'b');
        tee.write((byte) 'c');
        tee.close();
        assertEquals("abc", new String(sink.toByteArray()));
        assertEquals("ab", new String(tee.capturedBytes()));
        assertTrue(tee.isTruncated());
    }

    @Test
    public void zeroCapCapturesNothingAndAlwaysTruncated() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        TeeOutputStream tee = new TeeOutputStream(sink, 0);
        tee.write((byte) 'a');
        tee.write("xyz".getBytes());
        tee.close();
        assertEquals("axyz", new String(sink.toByteArray()));
        assertEquals(0, tee.capturedBytes().length);
        assertTrue(tee.isTruncated());
    }
}
