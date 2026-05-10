package xyz.winhok.nettap;

import java.nio.charset.StandardCharsets;

/**
 * Converts the bytes accumulated by {@link CronetBodyAccumulator} into a
 * {@link CaptureBody}, applying the same textual/binary/unknown policy used
 * elsewhere in the project.
 *
 * <p>Lives in its own class so unit tests can exercise the decoder without
 * loading {@link CronetUrlRequestHook}, which transitively links against
 * Xposed APIs that are {@code compileOnly} and therefore absent from the
 * test classpath.
 *
 * <p>This pass intentionally does not handle gzip/deflate: Cronet hooks see
 * the on-the-wire bytes after framework decoding in most cases, and folding
 * decompression in here would duplicate {@code ReflectiveOkHttp} without
 * adding test value. Binary or unrecognized content-types are reported as
 * omitted with a stable, log-greppable reason string.
 */
final class CronetResponseBodyDecoder {

    private CronetResponseBodyDecoder() {
    }

    /**
     * Decode {@code bytes} for a single Cronet response.
     *
     * @param bytes         payload accumulated across {@code onReadCompleted}
     *                      callbacks; may be {@code null} or empty
     * @param contentType   raw {@code content-type} header value, may be {@code null}
     * @param encoding      raw {@code content-encoding} header value, may be {@code null}
     * @param contentLength total bytes observed (including any bytes dropped by
     *                      truncation), threaded through verbatim
     * @param truncated     {@code true} when the accumulator dropped trailing bytes
     * @return a {@link CaptureBody} that is either textual (UTF-8 decoded) or
     *         omitted with a reason string
     */
    static CaptureBody decodeCronetResponseBody(
            byte[] bytes,
            String contentType,
            String encoding,
            long contentLength,
            boolean truncated
    ) {
        if (bytes == null || bytes.length == 0) {
            return CaptureBody.omitted(
                    contentType,
                    contentLength,
                    encoding,
                    "Cronet response body empty"
            );
        }
        BodyCapturePolicy.Decision decision = BodyCapturePolicy.classify(contentType);
        if (decision != BodyCapturePolicy.Decision.TEXTUAL) {
            String reason = decision == BodyCapturePolicy.Decision.BINARY
                    ? "Cronet response body content-type is binary"
                    : "Cronet response body content-type unsupported";
            return CaptureBody.omitted(contentType, contentLength, encoding, reason);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return CaptureBody.text(contentType, contentLength, encoding, truncated, text);
    }
}
