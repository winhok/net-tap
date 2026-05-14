package xyz.winhok.nettap;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class CaptureBodyTest {
    @Test
    public void cappedBytesOmitsMissingInputAndMarksTruncatedPrefixes() {
        assertTrue(CaptureBody.fromCappedBytes(null, 4, "empty")
                .toJson()
                .contains("\"omittedReason\":\"empty\""));
        assertTrue(CaptureBody.fromCappedBytes(new byte[0], 4, "empty")
                .toJson()
                .contains("\"omittedReason\":\"empty\""));

        String json = CaptureBody.fromCappedBytes(
                "abcdef".getBytes(StandardCharsets.UTF_8),
                4,
                "empty"
        ).toJson();

        assertTrue(json.contains("\"contentLength\":6"));
        assertTrue(json.contains("\"truncated\":true"));
        assertTrue(json.contains("\"text\":\"abcd\""));
    }
}
