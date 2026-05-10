package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class MetricsReporterTest {

    @Before
    public void setUp() {
        MetricsReporter.resetForTesting();
    }

    @After
    public void tearDown() {
        MetricsReporter.resetForTesting();
    }

    @Test
    public void snapshotEmptyBeforeAnyInc() {
        assertEquals(
                "no metrics recorded for com.foo",
                MetricsReporter.snapshot("com.foo"));
    }

    @Test
    public void incInstalledAndCapturedAreIndependent() {
        MetricsReporter.incInstalled("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN);
        MetricsReporter.incInstalled("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN);
        MetricsReporter.incCaptured("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN);
        assertEquals(
                2L,
                MetricsReporter.get("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN, "installed"));
        assertEquals(
                1L,
                MetricsReporter.get("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN, "captured"));
    }

    @Test
    public void snapshotIncludesAllLayersForPackage() {
        MetricsReporter.incInstalled("com.a", MetricsReporter.LAYER_CRONET);
        MetricsReporter.incInstalled("com.a", MetricsReporter.LAYER_GRPC);
        MetricsReporter.incInstalled("com.b", MetricsReporter.LAYER_HURL);
        String s = MetricsReporter.snapshot("com.a");
        if (!s.contains("CRONET") || !s.contains("GRPC")) {
            throw new AssertionError("snapshot missing layers: " + s);
        }
        if (s.contains("HURL")) {
            throw new AssertionError("snapshot leaked other package: " + s);
        }
    }

    @Test
    public void resetClearsAllCounters() {
        MetricsReporter.incInstalled("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN);
        MetricsReporter.resetForTesting();
        assertEquals(
                0L,
                MetricsReporter.get("com.foo", MetricsReporter.LAYER_OKHTTP_CHAIN, "installed"));
    }
}
