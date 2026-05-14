package xyz.winhok.nettap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RuntimeCaptureConfig {
    public static final String PREFERENCES_NAME = "nettap_ui_prefs";
    public static final String FILTER_URL_REGEX = "filter.url_regex";
    public static final String FILTER_PACKAGE_ALLOWLIST = "filter.package_allowlist";
    public static final String HOOK_CRONET_QUIC_KEYLOG = "hook.cronet_quic_keylog";
    private static final String LEGACY_HOOK_CRONET_KEYLOG = "hook.cronet_keylog";
    public static final int DEFAULT_REALTIME_PORT = 39287;
    public static final int DEFAULT_REALTIME_QUEUE_CAPACITY = 128;
    public static final int DEFAULT_REALTIME_TIMEOUT_MS = 50;
    private static final long PREFS_REFRESH_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private static volatile boolean realtimeTransportEnabled = true;
    private static volatile int realtimePort = DEFAULT_REALTIME_PORT;
    private static volatile int realtimeQueueCapacity = DEFAULT_REALTIME_QUEUE_CAPACITY;
    private static volatile int realtimeTimeoutMs = DEFAULT_REALTIME_TIMEOUT_MS;
    private static volatile boolean builderInterceptorHookEnabled =
            CaptureConfig.ENABLE_BUILDER_INTERCEPTOR_HOOK;
    private static volatile boolean tlsKeylogEnabled = CaptureConfig.ENABLE_TLS_KEYLOG;
    private static volatile boolean cronetQuicKeylogEnabled =
            CaptureConfig.ENABLE_CRONET_QUIC_KEYLOG;
    private static volatile String urlRegex = "";
    private static volatile List<Pattern> urlPatterns = Collections.emptyList();
    private static volatile Set<String> packageAllowlist = Collections.emptySet();
    private static volatile long lastPrefsRefreshNanos;

    private RuntimeCaptureConfig() {
    }

    public static boolean isRealtimeTransportEnabled() {
        return realtimeTransportEnabled;
    }

    public static int getRealtimePort() {
        return realtimePort;
    }

    public static int getRealtimeQueueCapacity() {
        return realtimeQueueCapacity;
    }

    public static int getRealtimeTimeoutMs() {
        return realtimeTimeoutMs;
    }

    public static boolean isBuilderInterceptorHookEnabled() {
        return builderInterceptorHookEnabled;
    }

    public static boolean isTlsKeylogEnabled() {
        return tlsKeylogEnabled;
    }

    public static boolean isCronetQuicKeylogEnabled() {
        return cronetQuicKeylogEnabled;
    }

    public static String getUrlRegex() {
        return urlRegex;
    }

    public static Set<String> getPackageAllowlist() {
        return packageAllowlist;
    }

    public static void applyFilters(String regex, Set<String> allowlist) {
        urlRegex = regex == null ? "" : regex.trim();
        urlPatterns = compilePatterns(urlRegex);
        packageAllowlist = immutableCleanSet(allowlist);
    }

    public static boolean shouldCapture(String packageName, String url) {
        Set<String> allowlist = packageAllowlist;
        if (!allowlist.isEmpty() && !allowlist.contains(packageName)) {
            return false;
        }
        List<Pattern> patterns = urlPatterns;
        if (patterns.isEmpty()) {
            return true;
        }
        if (url == null) {
            return false;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(url).find()) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldRecord(CaptureEvent event) {
        if (event == null) {
            return false;
        }
        if (BuilderInterceptorHook.HOOK_NAME.equals(event.getHook())
                && !builderInterceptorHookEnabled) {
            return false;
        }
        return shouldCapture(event.getPackageName(), event.getUrl());
    }

    public static void applyOverrides(
            boolean realtimeEnabled,
            int port,
            int queueCapacity,
            int timeoutMs,
            boolean builderInterceptorEnabled,
            boolean tlsEnabled,
            boolean cronetKeylogEnabled
    ) {
        realtimeTransportEnabled = realtimeEnabled;
        realtimePort = clamp(port, 1, 65535, DEFAULT_REALTIME_PORT);
        realtimeQueueCapacity = clamp(
                queueCapacity,
                1,
                4096,
                DEFAULT_REALTIME_QUEUE_CAPACITY
        );
        realtimeTimeoutMs = clamp(timeoutMs, 1, 5000, DEFAULT_REALTIME_TIMEOUT_MS);
        builderInterceptorHookEnabled = builderInterceptorEnabled;
        tlsKeylogEnabled = tlsEnabled;
        cronetQuicKeylogEnabled = cronetKeylogEnabled;
        RealtimeSinkClient.reconfigureDefault();
    }

    public static void refreshFromXSharedPreferences() {
        try {
            Class<?> prefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
            Constructor<?> constructor = prefsClass.getConstructor(String.class, String.class);
            Object prefs = constructor.newInstance("xyz.winhok.nettap", PREFERENCES_NAME);
            Method reload = prefsClass.getMethod("reload");
            Method getBoolean = prefsClass.getMethod("getBoolean", String.class, boolean.class);
            Method getInt = prefsClass.getMethod("getInt", String.class, int.class);
            Method getString = prefsClass.getMethod("getString", String.class, String.class);
            Method getStringSet = prefsClass.getMethod("getStringSet", String.class, Set.class);
            reload.invoke(prefs);
            boolean legacyCronetKeylog = (Boolean) getBoolean.invoke(
                    prefs,
                    LEGACY_HOOK_CRONET_KEYLOG,
                    cronetQuicKeylogEnabled
            );
            boolean cronetKeylog = (Boolean) getBoolean.invoke(
                    prefs,
                    HOOK_CRONET_QUIC_KEYLOG,
                    legacyCronetKeylog
            );
            applyOverrides(
                    (Boolean) getBoolean.invoke(prefs, "transport.enabled", realtimeTransportEnabled),
                    (Integer) getInt.invoke(prefs, "transport.port", realtimePort),
                    realtimeQueueCapacity,
                    realtimeTimeoutMs,
                    (Boolean) getBoolean.invoke(prefs, "hook.builder_interceptor", builderInterceptorHookEnabled),
                    (Boolean) getBoolean.invoke(prefs, "hook.tls_keylog", tlsKeylogEnabled),
                    cronetKeylog
            );
            applyFilters(
                    (String) getString.invoke(prefs, FILTER_URL_REGEX, urlRegex),
                    castStringSet(getStringSet.invoke(
                            prefs,
                            FILTER_PACKAGE_ALLOWLIST,
                            packageAllowlist
                    ))
            );
        } catch (Throwable ignored) {
        }
    }

    public static void refreshFromXSharedPreferencesIfStale() {
        long now = System.nanoTime();
        long lastRefresh = lastPrefsRefreshNanos;
        if (lastRefresh != 0L && now - lastRefresh < PREFS_REFRESH_INTERVAL_NANOS) {
            return;
        }
        synchronized (RuntimeCaptureConfig.class) {
            lastRefresh = lastPrefsRefreshNanos;
            if (lastRefresh != 0L && now - lastRefresh < PREFS_REFRESH_INTERVAL_NANOS) {
                return;
            }
            lastPrefsRefreshNanos = now;
            refreshFromXSharedPreferences();
        }
    }

    public static void resetForTests() {
        lastPrefsRefreshNanos = 0L;
        applyOverrides(
                true,
                DEFAULT_REALTIME_PORT,
                DEFAULT_REALTIME_QUEUE_CAPACITY,
                DEFAULT_REALTIME_TIMEOUT_MS,
                CaptureConfig.ENABLE_BUILDER_INTERCEPTOR_HOOK,
                CaptureConfig.ENABLE_TLS_KEYLOG,
                CaptureConfig.ENABLE_CRONET_QUIC_KEYLOG
        );
        applyFilters("", Collections.emptySet());
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private static List<Pattern> compilePatterns(String regex) {
        if (regex == null || regex.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Pattern> patterns = new ArrayList<>();
        for (String line : regex.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Pattern pattern = compilePattern(trimmed);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        return Collections.unmodifiableList(patterns);
    }

    private static Pattern compilePattern(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static Set<String> immutableCleanSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        HashSet<String> result = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(value.trim());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> castStringSet(Object value) {
        if (value instanceof Set) {
            return (Set<String>) value;
        }
        return Collections.emptySet();
    }
}
