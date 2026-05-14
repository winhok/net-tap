package xyz.winhok.nettap.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HookSettingStatusFormatterTest {
    @Test
    public void formatsEnabledAndDisabledRestartHints() {
        assertEquals(
                "Enabled · Restart host app to apply",
                HookSettingStatusFormatter.format(true)
        );
        assertEquals(
                "Disabled · Restart host app to apply",
                HookSettingStatusFormatter.format(false)
        );
    }
}
