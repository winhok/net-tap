package xyz.winhok.nettap.ui;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import xyz.winhok.nettap.RuntimeCaptureConfig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class UiPreferencesTest {
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        RuntimeCaptureConfig.resetForTests();
        Context context = RuntimeEnvironment.getApplication();
        sharedPreferences = context.getSharedPreferences(UiPreferences.NAME, Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        sharedPreferences.edit().clear().commit();
        RuntimeCaptureConfig.resetForTests();
    }

    @Test
    public void savesDocumentedCronetQuicKeylogPreferenceKey() {
        sharedPreferences.edit()
                .putBoolean("hook.cronet_keylog", true)
                .commit();
        UiPreferences preferences = new UiPreferences(RuntimeEnvironment.getApplication());

        preferences.save(true, 39287, 2000, 100, true, true, false);

        assertTrue(sharedPreferences.contains(RuntimeCaptureConfig.HOOK_CRONET_QUIC_KEYLOG));
        assertFalse(sharedPreferences.getBoolean(RuntimeCaptureConfig.HOOK_CRONET_QUIC_KEYLOG, true));
        assertFalse(sharedPreferences.contains("hook.cronet_keylog"));
    }

    @Test
    public void readsLegacyCronetKeylogPreferenceWhenDocumentedKeyIsMissing() {
        sharedPreferences.edit()
                .putBoolean("hook.cronet_keylog", false)
                .commit();

        UiPreferences preferences = new UiPreferences(RuntimeEnvironment.getApplication());

        assertFalse(preferences.isCronetKeylogEnabled());
    }

    @Test
    public void saveAppliesTransportAndHookSwitchesToRuntimeConfig() {
        UiPreferences preferences = new UiPreferences(RuntimeEnvironment.getApplication());

        preferences.save(false, 48123, 2500, 120, false, false, false);

        assertFalse(RuntimeCaptureConfig.isRealtimeTransportEnabled());
        assertEquals(48123, RuntimeCaptureConfig.getRealtimePort());
        assertFalse(RuntimeCaptureConfig.isBuilderInterceptorHookEnabled());
        assertFalse(RuntimeCaptureConfig.isTlsKeylogEnabled());
        assertFalse(RuntimeCaptureConfig.isCronetQuicKeylogEnabled());
    }

    @Test
    public void opensPreferencesFileUsedByXSharedPreferencesFallback() {
        UiPreferences preferences = new UiPreferences(RuntimeEnvironment.getApplication());

        preferences.save(false, 48123, 2500, 120, false, false, false);

        SharedPreferences reopened = UiPreferences.openPreferences(RuntimeEnvironment.getApplication());
        assertFalse(reopened.getBoolean("transport.enabled", true));
        assertEquals(48123, reopened.getInt("transport.port", 0));
    }

    @Test
    public void themeModeDefaultsToSystemAndSanitizesValues() {
        UiPreferences preferences = new UiPreferences(RuntimeEnvironment.getApplication());

        assertEquals(UiPreferences.THEME_SYSTEM, preferences.getThemeMode());

        preferences.saveThemeMode(UiPreferences.THEME_DARK);
        assertEquals(UiPreferences.THEME_DARK, preferences.getThemeMode());

        preferences.saveThemeMode("unexpected");
        assertEquals(UiPreferences.THEME_SYSTEM, preferences.getThemeMode());
    }

    @Test
    public void resetDefaultsRestoresSystemThemeMode() {
        UiPreferences preferences = new UiPreferences(RuntimeEnvironment.getApplication());

        preferences.saveThemeMode(UiPreferences.THEME_LIGHT);
        preferences.resetDefaults();

        assertEquals(UiPreferences.THEME_SYSTEM, preferences.getThemeMode());
    }
}
