package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public final class GrpcMetadataTest {

    /** Mirrors {@code io.grpc.Metadata} enough for reflective extraction. */
    @SuppressWarnings("unused")
    private static final class FakeMetadata {
        private Object[] namesAndValues;

        FakeMetadata(Object[] namesAndValues) {
            this.namesAndValues = namesAndValues;
        }
    }

    @Test
    public void extractHeadersNullMetadata() {
        LinkedHashMap<String, String> result = GrpcMetadata.extractHeaders(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void extractHeadersStringPairs() {
        FakeMetadata md = new FakeMetadata(new Object[]{
                "content-type", "application/grpc",
                "user-agent", "grpc-java/1.50.0"
        });
        Map<String, String> result = GrpcMetadata.extractHeaders(md);
        assertEquals("application/grpc", result.get("content-type"));
        assertEquals("grpc-java/1.50.0", result.get("user-agent"));
    }

    @Test
    public void extractHeadersBytePairsDecodedAsUtf8() {
        FakeMetadata md = new FakeMetadata(new Object[]{
                "x-custom".getBytes(),
                "value".getBytes()
        });
        Map<String, String> result = GrpcMetadata.extractHeaders(md);
        assertEquals("value", result.get("x-custom"));
    }

    @Test
    public void extractHeadersRedactsAuthorization() {
        FakeMetadata md = new FakeMetadata(new Object[]{
                "authorization", "Bearer secret-token",
                "cookie", "session=abc",
                "x-api-key", "xyz",
                "x-trace", "visible"
        });
        Map<String, String> result = GrpcMetadata.extractHeaders(md);
        assertEquals("<redacted>", result.get("authorization"));
        assertEquals("<redacted>", result.get("cookie"));
        assertEquals("<redacted>", result.get("x-api-key"));
        assertEquals("visible", result.get("x-trace"));
    }

    @Test
    public void extractHeadersBinKeyGetsPlaceholder() {
        byte[] binaryValue = new byte[]{1, 2, 3, 4, 5};
        FakeMetadata md = new FakeMetadata(new Object[]{
                "metadata-bin", binaryValue
        });
        Map<String, String> result = GrpcMetadata.extractHeaders(md);
        assertEquals("[bin 5B]", result.get("metadata-bin"));
    }

    @Test
    public void extractHeadersEmptyNamesAndValues() {
        FakeMetadata md = new FakeMetadata(new Object[]{});
        assertTrue(GrpcMetadata.extractHeaders(md).isEmpty());
    }

    @Test
    public void extractHeadersSkipsBlankNames() {
        FakeMetadata md = new FakeMetadata(new Object[]{
                "", "ignored",
                null, "ignored-too",
                "keep", "value"
        });
        Map<String, String> result = GrpcMetadata.extractHeaders(md);
        assertEquals(1, result.size());
        assertEquals("value", result.get("keep"));
        assertFalse(result.containsKey(""));
    }
}
