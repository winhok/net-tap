package xyz.winhok.nettap;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class ThemeContractTest {
    @Test
    public void appThemeUsesMaterial3DayNightNoActionBar() throws Exception {
        String themes = read("src/main/res/values/themes.xml");

        assertTrue(themes.contains("parent=\"Theme.Material3.DayNight.NoActionBar\""));
        assertTrue(themes.contains("name=\"android:windowLightStatusBar\">true"));
    }

    @Test
    public void nightThemeDisablesLightStatusBar() throws Exception {
        String nightThemes = read("src/main/res/values-night/themes.xml");

        assertTrue(nightThemes.contains("parent=\"Theme.Material3.DayNight.NoActionBar\""));
        assertTrue(nightThemes.contains("name=\"android:windowLightStatusBar\">false"));
    }

    @Test
    public void splashUsesPrimaryColorBackground() throws Exception {
        String themes = read("src/main/res/values/themes.xml");

        assertTrue(themes.contains("windowSplashScreenBackground"));
        assertTrue(themes.contains("@color/nettap_primary"));
        assertTrue(themes.contains("windowSplashScreenAnimatedIcon"));
        assertTrue(themes.contains("@mipmap/ic_launcher"));
    }

    @Test
    public void mainActivityAppliesDynamicColorsAndEdgeToEdge() throws Exception {
        String mainActivity = read("src/main/java/xyz/winhok/nettap/ui/MainActivity.java");

        assertTrue(mainActivity.contains("DynamicColors.applyToActivityIfAvailable(this)"));
        assertTrue(mainActivity.contains("EdgeToEdge.enable(this)"));
    }

    @Test
    public void mainActivityAppliesStoredThemeMode() throws Exception {
        String mainActivity = read("src/main/java/xyz/winhok/nettap/ui/MainActivity.java");

        assertTrue(mainActivity.contains("applyThemeMode(preferences.getThemeMode())"));
        assertTrue(mainActivity.contains("AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM"));
        assertTrue(mainActivity.contains("AppCompatDelegate.MODE_NIGHT_NO"));
        assertTrue(mainActivity.contains("AppCompatDelegate.MODE_NIGHT_YES"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
