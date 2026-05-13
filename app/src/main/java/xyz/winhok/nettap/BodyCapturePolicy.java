package xyz.winhok.nettap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BodyCapturePolicy {

    /**
     * Three-way classification of an HTTP content-type for body capture.
     * <ul>
     *   <li>{@code TEXTUAL}  — known textual type (whitelist hit); safe to decode as string.</li>
     *   <li>{@code BINARY}   — known binary type (blacklist hit); must not be decoded as text.</li>
     *   <li>{@code UNKNOWN}  — neither list matched (including null/empty input);
     *       the caller decides the default behavior.</li>
     * </ul>
     */
    public enum Decision {
        TEXTUAL,
        BINARY,
        UNKNOWN
    }

    /**
     * Binary content-type prefixes (lower-case, parameter part already stripped).
     * Covers common image/video/audio/font, archive, RPC, document, and binary
     * application types. Kept in a single list to make review and future
     * externalization (e.g. config-driven) trivial.
     */
    private static final List<String> BINARY_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "image/",
            "video/",
            "audio/",
            "font/",
            "application/octet-stream",
            "application/pb",
            "application/protobuf",
            "application/x-protobuf",
            "application/grpc",
            "application/grpc-web",
            "application/zip",
            "application/x-zip",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/x-7z-compressed",
            "application/wasm",
            "application/pdf",
            "application/cbor",
            "application/cose",
            "application/dns-message",
            "application/x-msgpack"
    ));

    private BodyCapturePolicy() {
    }

    /**
     * Returns {@code true} when the content-type is on the textual whitelist.
     * Behavior is preserved from the original implementation:
     * <ul>
     *   <li>{@link Decision#TEXTUAL} returns {@code true}</li>
     *   <li>{@link Decision#BINARY} returns {@code false}</li>
     *   <li>{@link Decision#UNKNOWN} returns {@code false} (includes null/empty input)</li>
     * </ul>
     * New code should prefer {@link #classify}; {@code isBinary}/{@code isTextual} are kept
     * for backward compatibility with simple binary check call sites.
     */
    public static boolean isTextual(String contentType) {
        return classify(contentType) == Decision.TEXTUAL;
    }

    /**
     * Returns {@code true} when the content-type is on the binary blacklist.
     * Independent of the textual whitelist; useful for callers that want to
     * actively reject binary payloads while still treating UNKNOWN as a
     * separate state.
     * New code should prefer {@link #classify}; {@code isBinary}/{@code isTextual} are kept
     * for backward compatibility with simple binary check call sites.
     */
    public static boolean isBinary(String contentType) {
        return classify(contentType) == Decision.BINARY;
    }

    /**
     * Three-way classification. Blacklist is checked before whitelist so that
     * any future whitelist entry which happens to share a binary prefix is
     * still rejected as binary.
     */
    public static Decision classify(String contentType) {
        String mediaType = normalize(contentType);
        if (mediaType == null) {
            return Decision.UNKNOWN;
        }
        if (matchesBinaryPrefix(mediaType)) {
            return Decision.BINARY;
        }
        if (matchesTextualWhitelist(mediaType)) {
            return Decision.TEXTUAL;
        }
        return Decision.UNKNOWN;
    }

    public static boolean isTruncated(long byteCount) {
        return byteCount > CaptureConfig.MAX_BODY_BYTES;
    }

    public static boolean isFormUrlEncoded(String contentType) {
        return "application/x-www-form-urlencoded".equals(normalize(contentType));
    }

    /**
     * Lower-case the media type and strip any {@code ;parameter} suffix.
     * Returns {@code null} when the input is missing or malformed (no subtype).
     */
    private static String normalize(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.US);
        int subtypeStart = mediaType.indexOf('/');
        if (subtypeStart < 0 || subtypeStart == mediaType.length() - 1) {
            return null;
        }
        return mediaType;
    }

    private static boolean matchesBinaryPrefix(String mediaType) {
        for (int i = 0, n = BINARY_PREFIXES.size(); i < n; i++) {
            if (mediaType.startsWith(BINARY_PREFIXES.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTextualWhitelist(String mediaType) {
        int subtypeStart = mediaType.indexOf('/');
        String subtype = mediaType.substring(subtypeStart + 1);
        return mediaType.startsWith("text/")
                || subtype.equals("json")
                || subtype.endsWith("+json")
                || subtype.equals("xml")
                || subtype.endsWith("+xml")
                || mediaType.equals("application/x-www-form-urlencoded")
                || isApplicationJavaScript(mediaType);
    }

    private static boolean isApplicationJavaScript(String mediaType) {
        return mediaType.equals("application/javascript")
                || mediaType.equals("application/x-javascript")
                || mediaType.equals("application/ecmascript")
                || mediaType.equals("application/x-ecmascript");
    }
}
