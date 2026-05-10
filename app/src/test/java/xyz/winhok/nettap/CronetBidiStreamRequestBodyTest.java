package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * Covers the pure byte-accumulation path. The hook classes themselves link
 * Xposed and are not testable on the JVM classpath.
 */
public final class CronetBidiStreamRequestBodyTest {

    @Test
    public void accumulatorCopiesRequestBytesExactly() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        byte[] payload = "hello bidi world".getBytes();
        ByteBuffer bb = ByteBuffer.allocate(64);
        bb.put(payload);
        int initialPosition = 0;
        int initialLimit = bb.limit();
        acc.appendFromBuffer(bb, payload.length, initialPosition, initialLimit, 1024);
        assertEquals(payload.length, acc.getBytes().length);
        assertEquals(new String(payload), new String(acc.getBytes()));
        assertTrue(!acc.isTruncated());
    }

    @Test
    public void accumulatorCapsToMaxBytes() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        byte[] payload = new byte[200];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        ByteBuffer bb = ByteBuffer.allocate(256);
        bb.put(payload);
        acc.appendFromBuffer(bb, payload.length, 0, bb.limit(), 50);
        assertEquals(50, acc.getBytes().length);
        assertTrue(acc.isTruncated());
        assertEquals(200L, acc.getTotalBytesObserved());
    }
}
