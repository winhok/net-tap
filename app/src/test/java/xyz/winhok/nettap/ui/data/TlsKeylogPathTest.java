package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TlsKeylogPathTest {
    @Test
    public void templateUsesHostPackagePlaceholder() {
        assertEquals(
                "/data/data/<host-package>/files/_tls_keylog.log",
                TlsKeylogPath.template()
        );
    }

    @Test
    public void forPackageUsesConcretePackageName() {
        assertEquals(
                "/data/data/com.example/files/_tls_keylog.log",
                TlsKeylogPath.forPackage("com.example")
        );
    }

    @Test
    public void blankPackageFallsBackToTemplate() {
        assertEquals(TlsKeylogPath.template(), TlsKeylogPath.forPackage(" "));
    }
}
