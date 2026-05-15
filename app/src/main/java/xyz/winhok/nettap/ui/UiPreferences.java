package xyz.winhok.nettap.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import xyz.winhok.nettap.RuntimeCaptureConfig;

public final class UiPreferences {
    public static final String NAME = RuntimeCaptureConfig.PREFERENCES_NAME;
    private static final String TRANSPORT_ENABLED = "transport.enabled";
    private static final String TRANSPORT_PORT = "transport.port";
    private static final String MAX_RECORDS = "ui.max_records";
    private static final String MAX_MEMORY_MB = "ui.max_memory_mb";
    private static final String THEME_MODE = "ui.theme_mode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    private static final String DEFAULT_SORT = "ui.default_sort";
    private static final String BUILDER_HOOK = "hook.builder_interceptor";
    private static final String TLS_KEYLOG = "hook.tls_keylog";
    private static final String CRONET_KEYLOG = RuntimeCaptureConfig.HOOK_CRONET_QUIC_KEYLOG;
    private static final String LEGACY_CRONET_KEYLOG = "hook.cronet_keylog";
    private static final String URL_REGEX = RuntimeCaptureConfig.FILTER_URL_REGEX;
    private static final String PACKAGE_ALLOWLIST = RuntimeCaptureConfig.FILTER_PACKAGE_ALLOWLIST;
    private final SharedPreferences prefs;

    public UiPreferences(Context context) {
        this.prefs = openPreferences(context);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("WorldReadableFiles")
    static SharedPreferences openPreferences(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            return appContext.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            return appContext.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        }
    }

    public boolean isTransportEnabled() {
        return prefs.getBoolean(TRANSPORT_ENABLED, true);
    }

    public int getTransportPort() {
        return UiPreferenceSanitizer.port(prefs.getInt(
                TRANSPORT_PORT,
                RuntimeCaptureConfig.DEFAULT_REALTIME_PORT
        ));
    }

    public int getMaxRecords() {
        return UiPreferenceSanitizer.positiveLimit(prefs.getInt(MAX_RECORDS, 2000));
    }

    public int getMaxMemoryMb() {
        return UiPreferenceSanitizer.positiveLimit(prefs.getInt(MAX_MEMORY_MB, 100));
    }

    public boolean isBuilderHookEnabled() {
        return prefs.getBoolean(BUILDER_HOOK, true);
    }

    public boolean isTlsKeylogEnabled() {
        return prefs.getBoolean(TLS_KEYLOG, true);
    }

    public boolean isCronetKeylogEnabled() {
        return prefs.getBoolean(CRONET_KEYLOG, prefs.getBoolean(LEGACY_CRONET_KEYLOG, true));
    }

    public SortOrder getDefaultSort() {
        return SortOrder.fromPreference(prefs.getString(
                DEFAULT_SORT,
                SortOrder.NEWEST_FIRST.toPreference()
        ));
    }

    public String getThemeMode() {
        return sanitizeThemeMode(prefs.getString(THEME_MODE, THEME_SYSTEM));
    }

    public void saveThemeMode(String themeMode) {
        prefs.edit()
                .putString(THEME_MODE, sanitizeThemeMode(themeMode))
                .apply();
    }

    public void saveDefaultSort(SortOrder sortOrder) {
        SortOrder value = sortOrder == null ? SortOrder.NEWEST_FIRST : sortOrder;
        prefs.edit()
                .putString(DEFAULT_SORT, value.toPreference())
                .apply();
    }

    public String getUrlRegex() {
        return prefs.getString(URL_REGEX, "");
    }

    public Set<String> getPackageAllowlist() {
        return prefs.getStringSet(PACKAGE_ALLOWLIST, Collections.emptySet());
    }

    public String getPackageAllowlistText() {
        StringBuilder text = new StringBuilder();
        for (String packageName : getPackageAllowlist()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(packageName);
        }
        return text.toString();
    }

    public void save(
            boolean transportEnabled,
            int port,
            int maxRecords,
            int maxMemoryMb,
            boolean builderHook,
            boolean tlsKeylog,
            boolean cronetKeylog
    ) {
        prefs.edit()
                .putBoolean(TRANSPORT_ENABLED, transportEnabled)
                .putInt(TRANSPORT_PORT, UiPreferenceSanitizer.port(port))
                .putInt(MAX_RECORDS, UiPreferenceSanitizer.positiveLimit(maxRecords))
                .putInt(MAX_MEMORY_MB, UiPreferenceSanitizer.positiveLimit(maxMemoryMb))
                .putBoolean(BUILDER_HOOK, builderHook)
                .putBoolean(TLS_KEYLOG, tlsKeylog)
                .putBoolean(CRONET_KEYLOG, cronetKeylog)
                .remove(LEGACY_CRONET_KEYLOG)
                .apply();
        applyToRuntime();
    }

    public void saveFilters(String urlRegex, String packageAllowlistText) {
        prefs.edit()
                .putString(URL_REGEX, urlRegex == null ? "" : urlRegex.trim())
                .putStringSet(PACKAGE_ALLOWLIST, parsePackageAllowlist(packageAllowlistText))
                .apply();
        applyToRuntime();
    }

    public void resetDefaults() {
        prefs.edit()
                .putBoolean(TRANSPORT_ENABLED, true)
                .putInt(TRANSPORT_PORT, RuntimeCaptureConfig.DEFAULT_REALTIME_PORT)
                .putInt(MAX_RECORDS, 2000)
                .putInt(MAX_MEMORY_MB, 100)
                .putString(THEME_MODE, THEME_SYSTEM)
                .putString(DEFAULT_SORT, SortOrder.NEWEST_FIRST.toPreference())
                .putBoolean(BUILDER_HOOK, true)
                .putBoolean(TLS_KEYLOG, true)
                .putBoolean(CRONET_KEYLOG, true)
                .remove(LEGACY_CRONET_KEYLOG)
                .putString(URL_REGEX, "")
                .putStringSet(PACKAGE_ALLOWLIST, Collections.emptySet())
                .apply();
        applyToRuntime();
    }

    public void applyToRuntime() {
        NetTapUiState.configureStore(getMaxRecords(), getMaxMemoryMb());
        RuntimeCaptureConfig.applyFilters(getUrlRegex(), getPackageAllowlist());
        RuntimeCaptureConfig.applyOverrides(
                isTransportEnabled(),
                getTransportPort(),
                RuntimeCaptureConfig.DEFAULT_REALTIME_QUEUE_CAPACITY,
                RuntimeCaptureConfig.DEFAULT_REALTIME_TIMEOUT_MS,
                isBuilderHookEnabled(),
                isTlsKeylogEnabled(),
                isCronetKeylogEnabled()
        );
    }

    public boolean applyRealtimeTransport() {
        if (isTransportEnabled()) {
            return NetTapUiState.startRealtimeServer(getTransportPort());
        }
        NetTapUiState.stopRealtimeServer();
        return true;
    }

    private static Set<String> parsePackageAllowlist(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptySet();
        }
        HashSet<String> result = new HashSet<>();
        for (String line : text.split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String sanitizeThemeMode(String value) {
        if (THEME_LIGHT.equals(value) || THEME_DARK.equals(value)) {
            return value;
        }
        return THEME_SYSTEM;
    }
}
