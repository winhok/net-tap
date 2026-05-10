package xyz.winhok.nettap;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BuilderInterceptorInjectorTest {
    @Test
    public void injectOnceAddsOnlyOneInterceptorPerBuilder() throws Exception {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        InvocationHandler handler = (proxy, method, args) -> null;

        assertTrue(BuilderInterceptorInjector.injectOnce(
                builder,
                Interceptor.class,
                Interceptor.class.getClassLoader(),
                handler
        ));
        assertFalse(BuilderInterceptorInjector.injectOnce(
                builder,
                Interceptor.class,
                Interceptor.class.getClassLoader(),
                handler
        ));

        assertEquals(1, builder.build().interceptors().size());
    }
}
