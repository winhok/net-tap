package xyz.winhok.nettap;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class ManifestContractTest {
    @Test
    public void declaresInternetPermissionForLoopbackRealtimeTransport() throws Exception {
        String manifest = new String(
                Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8
        );

        assertTrue(manifest.contains("android.permission.INTERNET"));
    }

    @Test
    public void declaresLauncherActivityAndMipmapIcons() throws Exception {
        String manifest = new String(
                Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8
        );

        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""));
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""));
        assertTrue(manifest.contains("android:name=\".ui.MainActivity\""));
        assertTrue(manifest.contains("android.intent.action.MAIN"));
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"));
    }

    @Test
    public void declaresXposedSharedPrefsForHookProcessConfigFallback() throws Exception {
        String manifest = new String(
                Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8
        );

        assertTrue(manifest.contains("android:name=\"xposedsharedprefs\""));
        assertTrue(manifest.contains("android:value=\"true\""));
    }
}
