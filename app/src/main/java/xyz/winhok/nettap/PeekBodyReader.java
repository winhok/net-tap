package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Function;

// Reflection helpers from the same package: {@link Reflect}.
// All cross-class lookups (no-arg invokes, signature-based searches) are
// delegated there to keep this file focused on the peek-and-buffer protocol.

/**
 * Generic, dependency-free helper for "peek" reading the body of an HTTP response without
 * consuming the underlying stream the application will later read.
 *
 * <p>This class is deliberately reflection-only and does not import any okhttp/okio types.
 * Target apps frequently ship okio under R8-mangled names, so all reads of
 * {@code source()}, {@code peek()}, {@code read(Buffer, long)} and
 * {@code readByteArray(long)} are looked up by shape rather than by symbolic class
 * reference. The host classloader of the body object is used so the resolved
 * {@code okio.Buffer}-equivalent class matches the one the app's body uses.
 *
 * <p>Typical usage from a hook:
 * <pre>{@code
 * PeekBodyReader.ProbeResult probe = PeekBodyReader.peekBytes(responseBody, MAX_BODY_BYTES);
 * if (probe != null) {
 *     boolean truncated = probe.isTruncated();
 *     byte[] payload = probe.getBytes();
 * }
 * }</pre>
 */
public final class PeekBodyReader {

    /**
     * Optional debug sink for swallowed reflection failures. Default no-op.
     * Tests, hooks, or diagnostic builds may reassign to capture the otherwise-silent
     * failures that cause this class to return {@code null} instead of bytes.
     *
     * <p>Volatile to allow safe replacement from any thread.
     */
    public static volatile Consumer<Throwable> debugSink = t -> {};

    private PeekBodyReader() {
    }

    /** Result of a peek probe; {@link #getBytes()} never includes the truncation-detection byte. */
    public static final class ProbeResult {
        private final byte[] bytes;
        private final boolean truncated;
        private final long totalRead;

        ProbeResult(byte[] bytes, boolean truncated, long totalRead) {
            this.bytes = bytes;
            this.truncated = truncated;
            this.totalRead = totalRead;
        }

        /** Bytes actually returned to the caller; {@code length <= maxBytes}. */
        public byte[] getBytes() {
            return bytes;
        }

        /** {@code true} if the source held strictly more than {@code maxBytes} bytes. */
        public boolean isTruncated() {
            return truncated;
        }

        /**
         * Total bytes drained from the peek source. May exceed {@code maxBytes} by exactly 1
         * to signal truncation, or equal the body size when not truncated.
         */
        public long getTotalRead() {
            return totalRead;
        }
    }

    /**
     * Probe a body-like object that exposes an okio source via either a no-arg
     * {@code source()} method (standard {@code okhttp3.ResponseBody}) or a {@code source}
     * field. The okio Buffer class is resolved from the body's classloader.
     *
     * <p>Returns {@code null} if any reflective step fails so callers can apply their own
     * fallback (for example, treating the body as omitted).
     */
    public static ProbeResult peekBytes(Object body, long maxBytes) {
        return peekBytes(body, maxBytes, PeekBodyReader::findOkioBufferClass);
    }

    /**
     * Variant of {@link #peekBytes(Object, long)} that accepts a caller-supplied resolver
     * for the okio {@code Buffer}-equivalent class. Useful for apps that ship okio under
     * an arbitrary R8-mangled name only the caller knows how to locate.
     *
     * <p>Returns {@code null} if {@code body} or {@code bufferClassResolver} is null, if
     * the resolver returns {@code null}, or if any reflective step fails.
     */
    public static ProbeResult peekBytes(Object body, long maxBytes,
            Function<ClassLoader, Class<?>> bufferClassResolver) {
        if (body == null || bufferClassResolver == null) {
            return null;
        }
        try {
            ClassLoader loader = body.getClass().getClassLoader();
            Class<?> bufferClass = bufferClassResolver.apply(loader);
            if (bufferClass == null) {
                return null;
            }
            Object source = extractSource(body);
            if (source == null) {
                return null;
            }
            return peekBytesUsingBuffer(source, bufferClass, maxBytes);
        } catch (Throwable t) {
            debugSink.accept(t);
            return null;
        }
    }

    /**
     * Lower-level entrypoint for callers that already hold a source object and the
     * okio Buffer class (useful for non-OkHttp HTTP stacks). The {@code source} object
     * must expose a no-arg {@code peek()} that returns a fresh okio source.
     */
    public static ProbeResult peekBytesUsingBuffer(Object source, Class<?> bufferClass, long maxBytes) {
        if (source == null || bufferClass == null || maxBytes < 0L) {
            return null;
        }
        try {
            Object peeked;
            try {
                peeked = Reflect.invokeNoArg(source, "peek");
            } catch (Throwable t) {
                debugSink.accept(t);
                return null;
            }
            if (peeked == null) {
                return null;
            }

            Object sink = Reflect.newInstance(bufferClass);

            Method readMethod = Reflect.findReadIntoSinkMethod(peeked.getClass(), bufferClass);
            if (readMethod == null) {
                return null;
            }

            // Read up to maxBytes + 1 so we can flag truncation when the source is larger.
            long limit = maxBytes + 1L;
            long total = 0L;
            while (total < limit) {
                long n;
                try {
                    Object res = readMethod.invoke(peeked, sink, limit - total);
                    if (!(res instanceof Number)) {
                        break;
                    }
                    n = ((Number) res).longValue();
                } catch (Throwable t) {
                    debugSink.accept(t);
                    break;
                }
                if (n <= 0L) {
                    break;
                }
                total += n;
            }

            boolean truncated = total > maxBytes;
            long bytesToRead = truncated ? maxBytes : total;

            byte[] bytes;
            if (bytesToRead == 0L) {
                bytes = new byte[0];
            } else {
                Method readByteArray = Reflect.findMethodBySignature(
                        bufferClass, byte[].class, long.class);
                if (readByteArray == null) {
                    return null;
                }
                Object res = readByteArray.invoke(sink, bytesToRead);
                if (!(res instanceof byte[])) {
                    return null;
                }
                bytes = (byte[]) res;
            }
            return new ProbeResult(bytes, truncated, total);
        } catch (Throwable t) {
            debugSink.accept(t);
            return null;
        }
    }

    /**
     * Try to resolve the okio {@code Buffer} class through the given classloader.
     *
     * <p>Standard {@code okio.Buffer} is tried first. Some heavily obfuscated apps rename
     * okio entirely (R8 may relocate it under an arbitrary mangled package such as
     * {@code a.B}), so callers needing such fallbacks should look up the class themselves
     * and use {@link #peekBytesUsingBuffer(Object, Class, long)} directly.
     */
    public static Class<?> findOkioBufferClass(ClassLoader loader) {
        ClassLoader cl = loader == null ? PeekBodyReader.class.getClassLoader() : loader;
        try {
            return Class.forName("okio.Buffer", true, cl);
        } catch (Throwable ignored) {
        }
        // Intentionally no app-specific obfuscated names hardcoded here; see javadoc.
        return null;
    }

    /**
     * Extract the underlying source from a body-like object. Tries the no-arg
     * {@code source()} method first via {@link Reflect#findMethod(Class, String, Class[])},
     * then falls back to a same-named field walked up the declared-class chain.
     */
    private static Object extractSource(Object body) {
        if (body == null) {
            return null;
        }
        Method m = Reflect.findMethod(body.getClass(), "source");
        if (m != null) {
            try {
                return Reflect.invoke(body, m);
            } catch (RuntimeException re) {
                debugSink.accept(re);
                return null;
            }
        }
        Class<?> c = body.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("source");
                f.setAccessible(true);
                return f.get(body);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                debugSink.accept(t);
                return null;
            }
        }
        return null;
    }
}
