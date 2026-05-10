package xyz.winhok.nettap;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import xyz.winhok.nettap.ShadedClassRegistry.DiscoveredOkHttp;

public final class ShadedClassRegistryTest {

    @Before
    public void setUp() {
        ShadedClassRegistry.resetForTesting();
    }

    @After
    public void tearDown() {
        ShadedClassRegistry.resetForTesting();
    }

    @Test
    public void getReturnsNullBeforePut() {
        assertNull(ShadedClassRegistry.get(getClass().getClassLoader()));
    }

    @Test
    public void getReturnsStoredInstanceAfterPut() {
        DiscoveredOkHttp d = new DiscoveredOkHttp(
                null, null, null, null, null, null, null, null, null, false);
        ShadedClassRegistry.put(getClass().getClassLoader(), d);
        assertSame(d, ShadedClassRegistry.get(getClass().getClassLoader()));
    }

    @Test
    public void putWithNullArgsIsSafeNoop() {
        ShadedClassRegistry.put(null, null);
        ShadedClassRegistry.put(getClass().getClassLoader(), null);
        assertNull(ShadedClassRegistry.get(null));
        assertNull(ShadedClassRegistry.get(getClass().getClassLoader()));
    }
}
