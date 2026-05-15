package xyz.winhok.nettap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class PeekBodyReaderTest {

    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");

    private Consumer<Throwable> originalDebugSink;

    @Before
    public void saveDebugSink() {
        originalDebugSink = PeekBodyReader.debugSink;
    }

    @After
    public void restoreDebugSink() {
        PeekBodyReader.debugSink = originalDebugSink;
    }

    @Test
    public void peekBytesReturnsCompleteBytesForSmallBody() {
        byte[] payload = "hello, world".getBytes(StandardCharsets.UTF_8);
        ResponseBody body = ResponseBody.create(payload, TEXT);

        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(body, 1024);

        assertNotNull(result);
        assertFalse(result.isTruncated());
        assertEquals(payload.length, result.getTotalRead());
        assertArrayEquals(payload, result.getBytes());
    }

    @Test
    public void peekBytesIsNotTruncatedWhenUnderLimit() {
        byte[] payload = new byte[100];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        ResponseBody body = ResponseBody.create(payload, TEXT);

        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(body, 1024);

        assertNotNull(result);
        assertFalse(result.isTruncated());
        assertEquals(100L, result.getTotalRead());
        assertEquals(100, result.getBytes().length);
        assertArrayEquals(payload, result.getBytes());
    }

    @Test
    public void peekBytesIsTruncatedWhenOverLimit() {
        byte[] payload = new byte[2048];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        ResponseBody body = ResponseBody.create(payload, TEXT);

        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(body, 512);

        assertNotNull(result);
        assertTrue(result.isTruncated());
        assertTrue("totalRead should exceed maxBytes when truncated", result.getTotalRead() > 512L);
        assertEquals(512, result.getBytes().length);
        for (int i = 0; i < 512; i++) {
            assertEquals("byte " + i + " should match", payload[i], result.getBytes()[i]);
        }
    }

    @Test
    public void peekBytesAtExactLimitIsNotTruncated() {
        byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        ResponseBody body = ResponseBody.create(payload, TEXT);

        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(body, 256);

        assertNotNull(result);
        assertFalse("body length == maxBytes must NOT be flagged truncated", result.isTruncated());
        assertEquals(256L, result.getTotalRead());
        assertEquals(256, result.getBytes().length);
        assertArrayEquals(payload, result.getBytes());
    }

    @Test
    public void peekDoesNotConsumeOriginalBody() throws Exception {
        String text = "the quick brown fox jumps over the lazy dog";
        ResponseBody body = ResponseBody.create(text.getBytes(StandardCharsets.UTF_8), TEXT);

        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(body, 1024);
        assertNotNull(result);
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), result.getBytes());

        // The original body must still hand back the full payload after a peek.
        String fromBody = body.string();
        assertEquals(text, fromBody);
    }

    @Test
    public void peekBytesUsingBufferWorksWithRawBufferedSource() {
        byte[] payload = "raw source payload".getBytes(StandardCharsets.UTF_8);
        Buffer source = new Buffer();
        source.write(payload);
        long sizeBefore = source.size();

        PeekBodyReader.ProbeResult result =
                PeekBodyReader.peekBytesUsingBuffer(source, Buffer.class, 1024);

        assertNotNull(result);
        assertFalse(result.isTruncated());
        assertEquals(payload.length, result.getTotalRead());
        assertArrayEquals(payload, result.getBytes());
        assertEquals("peek must not consume the raw source", sizeBefore, source.size());
    }

    @Test
    public void findOkioBufferClassResolvesOkioBuffer() {
        Class<?> cls = PeekBodyReader.findOkioBufferClass(getClass().getClassLoader());

        assertNotNull(cls);
        assertEquals("okio.Buffer", cls.getName());
    }

    @Test
    public void peekBytesReturnsNullForNullBody() {
        assertNull(PeekBodyReader.peekBytes(null, 1024));
    }

    @Test
    public void peekBytesUsingBufferReturnsNullForNullSource() {
        assertNull(PeekBodyReader.peekBytesUsingBuffer(null, Buffer.class, 1024));
    }

    @Test
    public void peekBytesReturnsNullForBodyWithoutSource() {
        // Plain Object has no source() method or field — must not throw.
        Object notABody = new Object();
        assertNull(PeekBodyReader.peekBytes(notABody, 1024));
    }

    @Test
    public void peekBytesReturnsNullWhenSourceMethodThrows() {
        // ResponseBody whose source() always throws. peekBytes must swallow and return null
        // instead of propagating the exception out of the reflective call path.
        ResponseBody throwingBody = newThrowingBody(new IllegalStateException("closed"));

        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(throwingBody, 1024);
        assertNull(result);
    }

    @Test
    public void peekBytesUsesCustomBufferClassResolver() {
        byte[] payload = "custom resolver payload".getBytes(StandardCharsets.UTF_8);
        ResponseBody body = ResponseBody.create(payload, TEXT);

        PeekBodyReader.ProbeResult result =
                PeekBodyReader.peekBytes(body, 1024, loader -> Buffer.class);

        assertNotNull(result);
        assertFalse(result.isTruncated());
        assertArrayEquals(payload, result.getBytes());
    }

    @Test
    public void peekBytesReturnsNullWhenResolverReturnsNull() {
        ResponseBody body = ResponseBody.create("payload".getBytes(StandardCharsets.UTF_8), TEXT);

        PeekBodyReader.ProbeResult result =
                PeekBodyReader.peekBytes(body, 1024, loader -> null);

        assertNull(result);
    }

    @Test
    public void peekBytesReturnsNullWhenResolverIsNull() {
        ResponseBody body = ResponseBody.create("payload".getBytes(StandardCharsets.UTF_8), TEXT);

        assertNull(PeekBodyReader.peekBytes(body, 1024, null));
    }

    @Test
    public void debugSinkReceivesSwallowedReflectionFailure() {
        List<Throwable> captured = new ArrayList<>();
        PeekBodyReader.debugSink = captured::add;

        IllegalStateException thrown = new IllegalStateException("closed");
        ResponseBody throwingBody = newThrowingBody(thrown);

        // source() throws IllegalStateException; extractSource swallows it and forwards
        // to debugSink. Result is null but the throwable must be observed by the sink.
        PeekBodyReader.ProbeResult result = PeekBodyReader.peekBytes(throwingBody, 1024);

        assertNull(result);
        assertEquals(1, captured.size());
        Throwable t = captured.get(0);
        assertNotNull(t);
        assertTrue(
                "expected IllegalStateException from throwing source(), got " + t.getClass(),
                t instanceof IllegalStateException);
        assertSame(thrown, t);
    }

    @Test
    public void debugSinkDefaultIsNoop() {
        // After @Before saves and (eventually) @After restores, the visible default
        // must be a non-null no-op consumer.
        Consumer<Throwable> sink = PeekBodyReader.debugSink;
        assertNotNull(sink);
        sink.accept(new RuntimeException("must not throw"));
        // Sanity: capturing-then-restoring round-trips.
        Consumer<Throwable> replacement = t -> {};
        PeekBodyReader.debugSink = replacement;
        assertSame(replacement, PeekBodyReader.debugSink);
    }

    private static ResponseBody newThrowingBody(RuntimeException toThrow) {
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return TEXT;
            }

            @Override
            public long contentLength() {
                return -1L;
            }

            @Override
            public BufferedSource source() {
                throw toThrow;
            }
        };
    }
}
