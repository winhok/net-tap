package xyz.winhok.nettap;

import org.junit.Test;
import org.junit.After;

import java.util.Collections;
import java.util.LinkedHashMap;

import de.robv.android.xposed.XSharedPreferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RuntimeCaptureConfigTest {
    @After
    public void tearDown() {
        XSharedPreferences.reset();
        RuntimeCaptureConfig.resetForTests();
    }

    @Test
    public void preferenceNameMatchesUiSpec() {
        assertEquals("nettap_ui_prefs", RuntimeCaptureConfig.PREFERENCES_NAME);
    }

    @Test
    public void captureFiltersRequireAllowedPackageAndMatchingUrlWhenConfigured() {
        RuntimeCaptureConfig.resetForTests();
        RuntimeCaptureConfig.applyFilters(
                "api\\.example\\.com",
                Collections.singleton("com.allowed")
        );

        assertTrue(RuntimeCaptureConfig.shouldCapture(
                "com.allowed",
                "https://api.example.com/users"
        ));
        assertFalse(RuntimeCaptureConfig.shouldCapture(
                "com.blocked",
                "https://api.example.com/users"
        ));
        assertFalse(RuntimeCaptureConfig.shouldCapture(
                "com.allowed",
                "https://cdn.example.com/users"
        ));
    }

    @Test
    public void invalidUrlRegexFailsOpen() {
        RuntimeCaptureConfig.resetForTests();
        RuntimeCaptureConfig.applyFilters("[", Collections.emptySet());

        assertTrue(RuntimeCaptureConfig.shouldCapture("com.any", "https://cdn.example.com/users"));
    }

    @Test
    public void multipleUrlRegexLinesMatchAnyLine() {
        RuntimeCaptureConfig.resetForTests();
        RuntimeCaptureConfig.applyFilters(
                "api\\.example\\.com\ncdn\\.example\\.com",
                Collections.emptySet()
        );

        assertTrue(RuntimeCaptureConfig.shouldCapture(
                "com.any",
                "https://cdn.example.com/assets"
        ));
        assertTrue(RuntimeCaptureConfig.shouldCapture(
                "com.any",
                "https://api.example.com/users"
        ));
        assertFalse(RuntimeCaptureConfig.shouldCapture(
                "com.any",
            "https://other.example.com/users"
        ));
    }

    @Test
    public void staleXSharedPreferencesRefreshAppliesRuntimeOverrides() {
        RuntimeCaptureConfig.resetForTests();
        XSharedPreferences.reset();
        XSharedPreferences.putBoolean("transport.enabled", false);
        XSharedPreferences.putInt("transport.port", 48123);
        XSharedPreferences.putBoolean("hook.builder_interceptor", false);
        XSharedPreferences.putBoolean("hook.tls_keylog", false);
        XSharedPreferences.putBoolean(RuntimeCaptureConfig.HOOK_CRONET_QUIC_KEYLOG, false);
        XSharedPreferences.putString(RuntimeCaptureConfig.FILTER_URL_REGEX, "api\\.example\\.com");
        XSharedPreferences.putStringSet(
                RuntimeCaptureConfig.FILTER_PACKAGE_ALLOWLIST,
                Collections.singleton("com.allowed")
        );

        RuntimeCaptureConfig.refreshFromXSharedPreferencesIfStale();

        assertEquals(1, XSharedPreferences.reloadCount());
        assertFalse(RuntimeCaptureConfig.isRealtimeTransportEnabled());
        assertEquals(48123, RuntimeCaptureConfig.getRealtimePort());
        assertFalse(RuntimeCaptureConfig.isBuilderInterceptorHookEnabled());
        assertFalse(RuntimeCaptureConfig.isTlsKeylogEnabled());
        assertFalse(RuntimeCaptureConfig.isCronetQuicKeylogEnabled());
        assertTrue(RuntimeCaptureConfig.shouldCapture(
                "com.allowed",
                "https://api.example.com/users"
        ));
        assertFalse(RuntimeCaptureConfig.shouldCapture(
                "com.blocked",
                "https://api.example.com/users"
        ));
    }

    @Test
    public void builderInterceptorEventsStopWhenBuilderHookIsDisabled() {
        RuntimeCaptureConfig.resetForTests();
        CaptureEvent builderEvent = event(BuilderInterceptorHook.HOOK_NAME);
        CaptureEvent realCallEvent = event("RealCall.getResponseWithInterceptorChain$okhttp");

        RuntimeCaptureConfig.applyOverrides(
                true,
                RuntimeCaptureConfig.DEFAULT_REALTIME_PORT,
                RuntimeCaptureConfig.DEFAULT_REALTIME_QUEUE_CAPACITY,
                RuntimeCaptureConfig.DEFAULT_REALTIME_TIMEOUT_MS,
                false,
                true,
                true
        );

        assertFalse(RuntimeCaptureConfig.shouldRecord(builderEvent));
        assertTrue(RuntimeCaptureConfig.shouldRecord(realCallEvent));
    }

    private static CaptureEvent event(String hook) {
        return CaptureEvent.fromParsed(
                "request-1",
                "2026-05-14T00:00:00.000Z",
                "com.example",
                hook,
                "GET",
                "https://api.example.com/users",
                new LinkedHashMap<>(),
                CaptureBody.omitted(null, -1L, null, "empty"),
                200,
                "OK",
                new LinkedHashMap<>(),
                CaptureBody.omitted(null, -1L, null, "empty"),
                1L,
                null
        );
    }
}
