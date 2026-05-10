package xyz.winhok.nettap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import xyz.winhok.nettap.ShadedClassRegistry.DiscoveredOkHttp;

public final class ShadedOkHttpDiscoveryTest {

    @Before
    public void setUp() {
        ShadedClassRegistry.resetForTesting();
    }

    @After
    public void tearDown() {
        ShadedClassRegistry.resetForTesting();
    }

    @Test
    public void discoverHandlesNullClassLoader() {
        DiscoveredOkHttp result = ShadedOkHttpDiscovery.discover("test.pkg", null);
        assertNotNull(result);
        assertNull(result.okHttpClient);
        assertNull(result.realCall);
        assertNull(result.exchange);
        assertFalse(result.isShaded);
    }

    @Test
    public void discoverStockOkHttpFromTestClasspath() throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        DiscoveredOkHttp result = ShadedOkHttpDiscovery.discover("test.pkg", cl);

        assertNotNull(result);
        assertFalse("stock okhttp is not shaded", result.isShaded);
        assertSame(Class.forName("okhttp3.OkHttpClient", true, cl), result.okHttpClient);
        assertSame(
                Class.forName("okhttp3.internal.connection.RealCall", true, cl),
                result.realCall);
        assertSame(
                Class.forName("okhttp3.internal.connection.Exchange", true, cl),
                result.exchange);
        assertSame(
                Class.forName("okhttp3.internal.http.RealInterceptorChain", true, cl),
                result.realInterceptorChain);
        assertTrue(result.hasAny());
    }

    @Test
    public void discoverCachesResultPerClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        DiscoveredOkHttp first = ShadedOkHttpDiscovery.discover("test.pkg", cl);
        DiscoveredOkHttp second = ShadedOkHttpDiscovery.discover("test.pkg", cl);
        assertSame("second call must return cached instance", first, second);
    }

    @Test
    public void registryReturnsNullBeforeDiscover() {
        assertNull(ShadedClassRegistry.get(Thread.currentThread().getContextClassLoader()));
    }

    @Test
    public void pivotWhenOnlyRealInterceptorChainAvailable() throws Exception {
        final ClassLoader real = Thread.currentThread().getContextClassLoader();
        ClassLoader partial = new ClassLoader(real) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if ("okhttp3.internal.http.RealInterceptorChain".equals(name)
                        || "okhttp3.Request".equals(name)
                        || "okhttp3.Response".equals(name)
                        || "okhttp3.Interceptor".equals(name)
                        || "okhttp3.Interceptor$Chain".equals(name)) {
                    return Class.forName(name, resolve, real);
                }
                if (name.startsWith("java.") || name.startsWith("kotlin.")) {
                    return real.loadClass(name);
                }
                throw new ClassNotFoundException(name);
            }
        };

        DiscoveredOkHttp result = ShadedOkHttpDiscovery.discover("test.pkg", partial);
        assertNotNull(result);
        assertNotNull("realInterceptorChain must resolve via pivot", result.realInterceptorChain);
        assertTrue("missing OkHttpClient/RealCall implies shaded", result.isShaded);
    }
}
