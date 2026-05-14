package xyz.winhok.nettap.ui;

public final class HookSettingStatusFormatter {
    private HookSettingStatusFormatter() {
    }

    public static String format(boolean enabled) {
        return (enabled ? "Enabled" : "Disabled") + " · Restart host app to apply";
    }
}
