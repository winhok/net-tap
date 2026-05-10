package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import xyz.winhok.nettap.BodyCapturePolicy.Decision;

public final class BodyCapturePolicyTest {
    @Test
    public void isTextualReturnsTrueForSupportedTextContentTypes() {
        assertTrue(BodyCapturePolicy.isTextual("application/json"));
        assertTrue(BodyCapturePolicy.isTextual("application/xml; charset=utf-8"));
        assertTrue(BodyCapturePolicy.isTextual("application/x-www-form-urlencoded"));
        assertTrue(BodyCapturePolicy.isTextual("application/javascript"));
        assertTrue(BodyCapturePolicy.isTextual("application/x-javascript"));
        assertTrue(BodyCapturePolicy.isTextual("application/ecmascript"));
        assertTrue(BodyCapturePolicy.isTextual("application/x-ecmascript"));
        assertTrue(BodyCapturePolicy.isTextual("text/javascript"));
        assertTrue(BodyCapturePolicy.isTextual("text/html"));
        assertTrue(BodyCapturePolicy.isTextual("text/plain"));
    }

    @Test
    public void isTextualReturnsFalseForBinaryContentTypes() {
        assertFalse(BodyCapturePolicy.isTextual("image/png"));
        assertFalse(BodyCapturePolicy.isTextual("application/octet-stream"));
    }

    @Test
    public void isTextualIgnoresBinaryContentTypeParameters() {
        assertFalse(BodyCapturePolicy.isTextual("application/octet-stream; name=response.json"));
        assertFalse(BodyCapturePolicy.isTextual("multipart/form-data; boundary=html-json-text"));
    }

    @Test
    public void isTextualDoesNotTreatArbitraryHtmlSubtypesAsText() {
        assertFalse(BodyCapturePolicy.isTextual("application/vnd.ms-htmlhelp"));
    }

    @Test
    public void isTextualReturnsFalseForMissingContentType() {
        assertFalse(BodyCapturePolicy.isTextual(null));
        assertFalse(BodyCapturePolicy.isTextual(""));
    }

    @Test
    public void isTruncatedReturnsTrueOnlyWhenByteCountExceedsLimit() {
        assertTrue(BodyCapturePolicy.isTruncated(CaptureConfig.MAX_BODY_BYTES + 1L));
        assertFalse(BodyCapturePolicy.isTruncated(CaptureConfig.MAX_BODY_BYTES));
    }

    @Test
    public void classifyReturnsTextualForWhitelistedTypes() {
        assertEquals(Decision.TEXTUAL, BodyCapturePolicy.classify("application/json"));
        assertEquals(Decision.TEXTUAL, BodyCapturePolicy.classify("text/html"));
    }

    @Test
    public void classifyReturnsBinaryForBlacklistedTypes() {
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("image/png"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("video/mp4"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("audio/mpeg"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/protobuf"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/grpc"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/wasm"));
    }

    @Test
    public void classifyReturnsUnknownForMissingInput() {
        assertEquals(Decision.UNKNOWN, BodyCapturePolicy.classify(null));
        assertEquals(Decision.UNKNOWN, BodyCapturePolicy.classify(""));
    }

    @Test
    public void classifyReturnsUnknownForUnclassifiedType() {
        assertEquals(Decision.UNKNOWN, BodyCapturePolicy.classify("application/foo-bar"));
    }

    @Test
    public void classifyIgnoresCaseAndParameters() {
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("Image/PNG; charset=binary"));
    }

    @Test
    public void isBinaryIsIndependentlyTestable() {
        assertTrue(BodyCapturePolicy.isBinary("image/png"));
        assertFalse(BodyCapturePolicy.isBinary("text/plain"));
        assertFalse(BodyCapturePolicy.isBinary(null));
    }

    @Test
    public void classifyOctetStreamWithFilenameParameterIsBinary() {
        assertEquals(
                Decision.BINARY,
                BodyCapturePolicy.classify("application/octet-stream; name=response.json"));
    }

    @Test
    public void classifyGrpcWebProtoIsBinary() {
        // grpc-web+proto starts with both "application/grpc" and "application/grpc-web";
        // either match is acceptable, the caller only cares it ends up BINARY.
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/grpc-web+proto"));
    }

    @Test
    public void classifyApplicationJavascriptStaysTextual() {
        // Boundary check: javascript must not be accidentally swallowed by any
        // future or current binary prefix entry.
        assertEquals(Decision.TEXTUAL, BodyCapturePolicy.classify("application/javascript"));
    }

    @Test
    public void classifyAdditionalBinaryProtocolsAsBinary() {
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/cbor"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/cose"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/dns-message"));
        assertEquals(Decision.BINARY, BodyCapturePolicy.classify("application/x-msgpack"));

        assertEquals(
                Decision.BINARY,
                BodyCapturePolicy.classify("application/cbor; charset=binary"));
        assertEquals(
                Decision.BINARY,
                BodyCapturePolicy.classify("application/cose; charset=binary"));
        assertEquals(
                Decision.BINARY,
                BodyCapturePolicy.classify("application/dns-message; charset=binary"));
        assertEquals(
                Decision.BINARY,
                BodyCapturePolicy.classify("application/x-msgpack; charset=binary"));
    }
}
