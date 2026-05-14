package xyz.winhok.nettap.ui;

import org.junit.Test;

import xyz.winhok.nettap.RuntimeCaptureConfig;

import static org.junit.Assert.assertEquals;

public final class UiPreferenceSanitizerTest {
    @Test
    public void keepsValidPort() {
        assertEquals(39288, UiPreferenceSanitizer.port(39288));
    }

    @Test
    public void fallsBackForInvalidPortRange() {
        assertEquals(RuntimeCaptureConfig.DEFAULT_REALTIME_PORT, UiPreferenceSanitizer.port(0));
        assertEquals(RuntimeCaptureConfig.DEFAULT_REALTIME_PORT, UiPreferenceSanitizer.port(70000));
    }

    @Test
    public void clampsPositiveLimitsToAtLeastOne() {
        assertEquals(1, UiPreferenceSanitizer.positiveLimit(0));
        assertEquals(8, UiPreferenceSanitizer.positiveLimit(8));
    }
}
