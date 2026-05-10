package xyz.winhok.nettap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CronetInstallRegistryTest {

    @Test
    public void markInstalledReturnsTrueOnlyForFirstLoaderAndClassNamePair() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };

        assertTrue(registry.markInstalled(loader, "org.chromium.net.impl.CronetUrlRequest"));
        assertFalse(registry.markInstalled(loader, "org.chromium.net.impl.CronetUrlRequest"));
        assertTrue(registry.isInstalled(loader, "org.chromium.net.impl.CronetUrlRequest"));
    }

    @Test
    public void differentClassNamesOnSameLoaderAreIndependent() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };

        assertTrue(registry.markInstalled(loader, "org.chromium.net.impl.CronetUrlRequest"));
        assertTrue(registry.markInstalled(loader, "com.ttnet.org.chromium.net.impl.CronetUrlRequest"));
        assertTrue(registry.isInstalled(loader, "org.chromium.net.impl.CronetUrlRequest"));
        assertTrue(registry.isInstalled(loader, "com.ttnet.org.chromium.net.impl.CronetUrlRequest"));
    }

    @Test
    public void unmarkInstalledAllowsRetryForSameLoaderAndClassName() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };
        String className = "org.chromium.net.impl.CronetUrlRequest";

        assertTrue(registry.markInstalled(loader, className));
        registry.unmarkInstalled(loader, className);

        assertFalse(registry.isInstalled(loader, className));
        assertTrue(registry.markInstalled(loader, className));
    }

    @Test
    public void nullLoaderOrClassNameNeverMarksInstalled() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) { };

        assertFalse(registry.markInstalled(null, "org.chromium.net.impl.CronetUrlRequest"));
        assertFalse(registry.markInstalled(loader, null));
        assertFalse(registry.isInstalled(null, "org.chromium.net.impl.CronetUrlRequest"));
        assertFalse(registry.isInstalled(loader, null));
    }
}
