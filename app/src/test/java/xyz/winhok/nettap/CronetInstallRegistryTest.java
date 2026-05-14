package xyz.winhok.nettap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void installOnceRejectsMissingInputsAndSkipsNullCandidates() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = getClass().getClassLoader();

        assertFalse(registry.installOnce(null, Collections.singletonList(String.class),
                null, "pkg", (cls, collector) -> { }));
        assertFalse(registry.installOnce(loader, null, null, "pkg", (cls, collector) -> { }));
        assertFalse(registry.installOnce(loader, Collections.singletonList(String.class),
                null, "pkg", null));
        assertFalse(registry.installOnce(loader, Collections.singletonList(null),
                null, "pkg", (cls, collector) -> fail("null candidates must be skipped")));
    }

    @Test
    public void installOnceRunsFactoryOnlyForFreshClasses() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = getClass().getClassLoader();
        AtomicInteger calls = new AtomicInteger();

        assertTrue(registry.installOnce(
                loader,
                Collections.singletonList(String.class),
                null,
                "pkg",
                (cls, collector) -> calls.incrementAndGet()
        ));
        assertTrue(registry.installOnce(
                loader,
                Collections.singletonList(String.class),
                null,
                "pkg",
                (cls, collector) -> calls.incrementAndGet()
        ));

        assertTrue(registry.isInstalled(loader, String.class.getName()));
        assertTrue(calls.get() == 1);
    }

    @Test
    public void installOnceRetriesClassAfterFactoryFailure() {
        CronetInstallRegistry registry = new CronetInstallRegistry();
        ClassLoader loader = getClass().getClassLoader();

        assertTrue(registry.installOnce(
                loader,
                Arrays.asList(Integer.class, Long.class),
                MetricsReporter.LAYER_CRONET,
                "pkg",
                (cls, collector) -> {
                    if (cls == Integer.class) {
                        throw new IllegalStateException("boom");
                    }
                }
        ));
        assertFalse(registry.isInstalled(loader, Integer.class.getName()));
        assertTrue(registry.isInstalled(loader, Long.class.getName()));

        assertTrue(registry.installOnce(
                loader,
                Collections.singletonList(Integer.class),
                null,
                "pkg",
                (cls, collector) -> { }
        ));
        assertTrue(registry.isInstalled(loader, Integer.class.getName()));

        assertFalse(registry.installOnce(
                loader,
                Collections.singletonList(Double.class),
                null,
                "pkg",
                (cls, collector) -> {
                    throw new IllegalStateException("still broken");
                }
        ));
        assertFalse(registry.isInstalled(loader, Double.class.getName()));
    }
}
