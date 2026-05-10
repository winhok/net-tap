package xyz.winhok.nettap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class CronetBodyAccumulatorTest {

    @Test
    public void appendShortDataExposesExactContent() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("hello");

        acc.appendFromBuffer(buf, 5, 0, 5, 1024);

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), acc.getBytes());
        assertFalse(acc.isTruncated());
        assertEquals(5L, acc.getTotalBytesObserved());
    }

    @Test
    public void multipleAppendsConcatenateInOrder() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();

        acc.appendFromBuffer(flippedBuffer("Hello"), 5, 0, 5, 1024);
        acc.appendFromBuffer(flippedBuffer(", world"), 7, 0, 7, 1024);

        assertArrayEquals(
                "Hello, world".getBytes(StandardCharsets.UTF_8),
                acc.getBytes()
        );
        assertFalse(acc.isTruncated());
        assertEquals(12L, acc.getTotalBytesObserved());
    }

    @Test
    public void truncatesWhenAppendExceedsMaxBytes() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("123456");

        acc.appendFromBuffer(buf, 6, 0, 6, 4);

        assertArrayEquals("1234".getBytes(StandardCharsets.UTF_8), acc.getBytes());
        assertTrue(acc.isTruncated());
        assertEquals("total bytes observed must include the truncated tail", 6L,
                acc.getTotalBytesObserved());
    }

    @Test
    public void doesNotMutateSourceBufferPositionOrLimit() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("payload");
        int positionBefore = buf.position();
        int limitBefore = buf.limit();

        acc.appendFromBuffer(buf, 7, 0, 7, 1024);

        assertEquals(positionBefore, buf.position());
        assertEquals(limitBefore, buf.limit());
    }

    @Test
    public void zeroBytesReadIsNoopAndDoesNotMarkTruncated() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("abc");

        acc.appendFromBuffer(buf, 0, 0, 3, 1024);

        assertEquals(0, acc.getBytes().length);
        assertFalse(acc.isTruncated());
        assertEquals(0L, acc.getTotalBytesObserved());
    }

    @Test
    public void maxBytesZeroTruncatesEverythingButStillObservesTotal() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("data");

        acc.appendFromBuffer(buf, 4, 0, 4, 0);

        assertEquals(0, acc.getBytes().length);
        assertTrue(acc.isTruncated());
        assertEquals(4L, acc.getTotalBytesObserved());
    }

    @Test
    public void readsCorrectlyEvenWhenBufferPositionIsAtLimit() {
        // Mirrors real Cronet onReadCompleted callback state:
        // bytes have been written, so buffer.position() has advanced past
        // initialPosition all the way to initialLimit. The accumulator must
        // still read [initialPosition, initialPosition + bytesRead) correctly.
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = postWriteBuffer("hello");
        assertEquals(
                "precondition: position must equal limit (Cronet post-write state)",
                buf.limit(),
                buf.position()
        );

        acc.appendFromBuffer(buf, 5, 0, 5, 1024);

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), acc.getBytes());
        assertFalse(acc.isTruncated());
        assertEquals(5L, acc.getTotalBytesObserved());
    }

    @Test
    public void initialPositionBeyondBufferCapacityIsIgnoredWithoutMutatingBuffer() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("abcd");
        int positionBefore = buf.position();
        int limitBefore = buf.limit();

        try {
            acc.appendFromBuffer(buf, 1, 8, 9, 1024);
        } catch (RuntimeException e) {
            fail("invalid Cronet callback bounds must not escape: " + e);
        }

        assertEquals(positionBefore, buf.position());
        assertEquals(limitBefore, buf.limit());
        assertEquals(0, acc.getBytes().length);
        assertFalse(acc.isTruncated());
        assertEquals(0L, acc.getTotalBytesObserved());
    }

    @Test
    public void bytesPastBufferCapacityAreSafelyClippedWithoutMutatingBuffer() {
        CronetBodyAccumulator acc = new CronetBodyAccumulator();
        ByteBuffer buf = flippedBuffer("abcd");
        int positionBefore = buf.position();
        int limitBefore = buf.limit();

        try {
            acc.appendFromBuffer(buf, 10, 2, 12, 1024);
        } catch (RuntimeException e) {
            fail("overflowing Cronet callback bounds must not escape: " + e);
        }

        assertEquals(positionBefore, buf.position());
        assertEquals(limitBefore, buf.limit());
        assertArrayEquals("cd".getBytes(StandardCharsets.UTF_8), acc.getBytes());
        assertFalse(acc.isTruncated());
        assertEquals(2L, acc.getTotalBytesObserved());
    }

    private static ByteBuffer flippedBuffer(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(bytes.length);
        buf.put(bytes);
        buf.flip();
        return buf;
    }

    private static ByteBuffer postWriteBuffer(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(bytes.length);
        buf.put(bytes);
        return buf;
    }
}
