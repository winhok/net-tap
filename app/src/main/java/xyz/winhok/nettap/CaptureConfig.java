package xyz.winhok.nettap;

public final class CaptureConfig {
    public static final String CAPTURE_FILENAME = "_okhttp_capture.jsonl";
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    public static final int LOGCAT_CHUNK_SIZE = 3000;
    // Logcat emission is skipped for JSON records larger than this. Compared
    // against String.length() (chars, not bytes) — the full JSONL file write
    // still receives the complete record.
    public static final int LOGCAT_MAX_JSON_CHARS = 128 * 1024;
    public static final boolean ENABLE_BUILDER_INTERCEPTOR_HOOK = true;

    // TLS key log — NSS SSL Key Log format, for Wireshark offline decrypt.
    // Conscrypt path covers TLS 1.2/1.3 over TCP (OkHttp/HURL/Apache/Netty/...).
    // Cronet path covers QUIC/HTTP3 + BoringSSL via experimental options.
    public static final String TLS_KEYLOG_FILENAME = "_tls_keylog.log";
    public static final boolean ENABLE_TLS_KEYLOG = true;
    public static final boolean ENABLE_CRONET_QUIC_KEYLOG = true;

    private CaptureConfig() {
    }
}
