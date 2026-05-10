package xyz.winhok.nettap;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-gRPC-call mutable state. Populated across hooks (start, sendMessage,
 * listener.onHeaders / onMessage / onClose) and finalized into a
 * {@link CaptureEvent} when the call terminates.
 *
 * <p>Access is serialized via the {@code mutex} monitor; the primary owner is
 * {@link GrpcCallInstaller} but {@link GrpcListenerProxy} also mutates the
 * response fields.
 */
final class GrpcCaptureState {
    private static final AtomicLong NEXT_ID = new AtomicLong();

    final Object mutex = new Object();
    final String id;
    final long startedNanos;
    final String packageName;

    String fullMethodName;
    String authority;
    final LinkedHashMap<String, String> requestHeaders = new LinkedHashMap<>();
    byte[] requestBodyFirstMessage;
    int requestBodyObserved;
    boolean requestBodyTruncated;

    final LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
    byte[] responseBodyFirstMessage;
    int responseBodyObserved;
    boolean responseBodyTruncated;

    int statusCode;
    String statusMessage;
    boolean recorded;

    GrpcCaptureState(String packageName) {
        this.packageName = packageName;
        this.startedNanos = System.nanoTime();
        this.id = "grpc-" + NEXT_ID.incrementAndGet();
    }
}
