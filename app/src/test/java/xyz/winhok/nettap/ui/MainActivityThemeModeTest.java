package xyz.winhok.nettap.ui;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class MainActivityThemeModeTest {
    @After
    public void tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED);
    }

    @Test
    public void lightThemeModeDisablesNightMode() {
        MainActivity.applyThemeMode(UiPreferences.THEME_LIGHT);

        assertEquals(
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.getDefaultNightMode()
        );
    }

    @Test
    public void darkThemeModeEnablesNightMode() {
        MainActivity.applyThemeMode(UiPreferences.THEME_DARK);

        assertEquals(
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.getDefaultNightMode()
        );
    }

    @Test
    public void systemAndUnexpectedThemeModesFollowSystem() {
        MainActivity.applyThemeMode(UiPreferences.THEME_SYSTEM);
        assertEquals(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode()
        );

        MainActivity.applyThemeMode("unexpected");
        assertEquals(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode()
        );
    }
}
