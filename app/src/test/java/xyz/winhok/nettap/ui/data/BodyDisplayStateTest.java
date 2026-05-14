package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BodyDisplayStateTest {
    @Test
    public void validJsonIsFormattedWithOkStatus() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "application/json",
                7,
                null,
                false,
                "{\"a\":1}"
        ));

        assertEquals("OK", state.getStatus());
        assertTrue(state.getText().contains("\n  \"a\""));
    }

    @Test
    public void invalidJsonFallsBackToRawWithFormatFailedStatus() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "application/json",
                7,
                null,
                false,
                "{\"a\""
        ));

        assertEquals("Format failed: raw shown", state.getStatus());
        assertEquals("{\"a\"", state.getText());
    }

    @Test
    public void omittedBodyShowsReason() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.omitted(
                "image/png",
                120,
                null,
                "binary content"
        ));

        assertEquals("Omitted: binary content", state.getStatus());
        assertEquals("", state.getText());
    }

    @Test
    public void truncatedBodyKeepsFormattedTextAndStatus() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "application/json",
                1024,
                null,
                true,
                "{\"ok\":true}"
        ));

        assertEquals("Truncated: 11 / 1024 bytes", state.getStatus());
        assertTrue(state.getText().contains("\"ok\""));
    }

    @Test
    public void truncatedBodyCountsUtf8Bytes() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "text/plain",
                8,
                null,
                true,
                "é"
        ));

        assertEquals("Truncated: 2 / 8 bytes", state.getStatus());
    }

    @Test
    public void binaryContentWithTextShowsBinaryBase64Status() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "image/png",
                4,
                "base64",
                false,
                "iVBORw=="
        ));

        assertEquals("Binary (base64)", state.getStatus());
        assertEquals("iVBORw==", state.getText());
        assertTrue(state.isBinaryBase64());
    }

    @Test
    public void detectsLanguageFromContentTypeAndText() {
        assertEquals("json", BodyDisplayState.from(CaptureBodySnapshot.text(
                "application/json",
                7,
                null,
                false,
                "{\"a\":1}"
        )).getLanguage());
        assertEquals("html", BodyDisplayState.from(CaptureBodySnapshot.text(
                null,
                13,
                null,
                false,
                "<html></html>"
        )).getLanguage());
        assertEquals("text", BodyDisplayState.from(CaptureBodySnapshot.text(
                "text/plain",
                5,
                null,
                false,
                "hello"
        )).getLanguage());
    }

    @Test
    public void largeBodyRequiresConfirmationBeforeLoad() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "text/plain",
                2L * 1024L * 1024L + 1L,
                null,
                false,
                "body"
        ));

        assertTrue(state.requiresLargeBodyConfirmation());
    }

    @Test
    public void largeJsonBodyStaysRawUntilUserConfirmsLoad() {
        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "application/json",
                2L * 1024L * 1024L + 1L,
                null,
                false,
                "{\"a\":1}"
        ));

        assertTrue(state.requiresLargeBodyConfirmation());
        assertEquals("{\"a\":1}", state.getText());
        assertEquals("json", state.getLanguage());
    }

    @Test
    public void fiftyKbJsonBodyFormatsWithoutLargeBodyGate() {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 2500; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(i).append(",\"ok\":true}");
        }
        json.append("]}");

        BodyDisplayState state = BodyDisplayState.from(CaptureBodySnapshot.text(
                "application/json",
                json.length(),
                null,
                false,
                json.toString()
        ));

        assertTrue(json.length() > 50 * 1024);
        assertFalse(state.requiresLargeBodyConfirmation());
        assertEquals("OK", state.getStatus());
        assertTrue(state.getText().contains("\"items\""));
        assertTrue(state.getText().contains("\"id\": 2499"));
    }
}
