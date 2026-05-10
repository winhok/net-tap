package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import xyz.winhok.nettap.ShadedClassRegistry.DiscoveredOkHttp;

/**
 * Resolves OkHttp class references for a host app, supporting both the stock
 * {@code okhttp3.*} layout and R8/ProGuard-obfuscated variants where classes
 * have been renamed or relocated under an app-owned namespace.
 *
 * <p>Strategy ladder:
 * <ol>
 *   <li>Stock fast path: load the canonical {@code okhttp3.*} FQNs.</li>
 *   <li>RealInterceptorChain pivot: {@code okhttp3.internal.http.RealInterceptorChain}
 *       is almost always preserved even when other OkHttp classes are renamed.
 *       Inspect its {@code proceed} method to recover the {@code Request} and
 *       {@code Response} types, and its declared fields to recover the
 *       interceptor list and owning call.</li>
 * </ol>
 *
 * <p>Discovery never throws — failures degrade to {@code null} class refs.
 */
public final class ShadedOkHttpDiscovery {

    private ShadedOkHttpDiscovery() {
    }

    public static DiscoveredOkHttp discover(String packageName, ClassLoader classLoader) {
        DiscoveredOkHttp cached = ShadedClassRegistry.get(classLoader);
        if (cached != null) {
            return cached;
        }
        if (classLoader == null) {
            DiscoveredOkHttp empty = new DiscoveredOkHttp(
                    null, null, null, null, null, null, null, null, null, false);
            return empty;
        }

        DiscoveredOkHttp result = tryStock(classLoader);
        if (result == null || !result.hasAny()) {
            result = tryPivot(classLoader);
        }
        if (result == null) {
            result = new DiscoveredOkHttp(
                    null, null, null, null, null, null, null, null, null, false);
        }

        log("[shaded-discovery] %s: %s", packageName, result);
        ShadedClassRegistry.put(classLoader, result);
        return result;
    }

    private static DiscoveredOkHttp tryStock(ClassLoader classLoader) {
        Class<?> client = loadOrNull(classLoader, "okhttp3.OkHttpClient");
        if (client == null) {
            return null;
        }
        Class<?> builder = loadOrNull(classLoader, "okhttp3.OkHttpClient$Builder");
        Class<?> realCall = loadOrNull(classLoader, "okhttp3.internal.connection.RealCall");
        if (realCall == null) {
            realCall = loadOrNull(classLoader, "okhttp3.RealCall");
        }
        Class<?> exchange = loadOrNull(classLoader, "okhttp3.internal.connection.Exchange");
        Class<?> interceptor = loadOrNull(classLoader, "okhttp3.Interceptor");
        Class<?> chain = loadOrNull(classLoader, "okhttp3.Interceptor$Chain");
        Class<?> request = loadOrNull(classLoader, "okhttp3.Request");
        Class<?> response = loadOrNull(classLoader, "okhttp3.Response");
        Class<?> ric = loadOrNull(classLoader, "okhttp3.internal.http.RealInterceptorChain");
        return new DiscoveredOkHttp(
                client, builder, realCall, exchange, interceptor, chain,
                request, response, ric, false);
    }

    /**
     * Pivot from {@code okhttp3.internal.http.RealInterceptorChain} even when
     * other OkHttp classes are renamed. In practice RealInterceptorChain keeps
     * its FQN under heavy R8 obfuscation because it sits in okhttp3's internal
     * http package, while app-facing OkHttp types are often relocated.
     */
    private static DiscoveredOkHttp tryPivot(ClassLoader classLoader) {
        Class<?> ric = loadOrNull(classLoader, "okhttp3.internal.http.RealInterceptorChain");
        if (ric == null) {
            return null;
        }
        Class<?> request = null;
        Class<?> response = null;
        Class<?> interceptor = null;
        Class<?> realCall = null;

        try {
            Method proceed = null;
            for (Method m : ric.getDeclaredMethods()) {
                if ("proceed".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    proceed = m;
                    break;
                }
            }
            if (proceed != null) {
                request = proceed.getParameterTypes()[0];
                response = proceed.getReturnType();
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field f : ric.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (t != null && List.class.isAssignableFrom(t) && interceptor == null) {
                    interceptor = loadOrNull(classLoader, "okhttp3.Interceptor");
                }
                String n = t == null ? "" : t.getName();
                if (n.endsWith(".RealCall") || n.endsWith("/RealCall")
                        || n.equals("okhttp3.Call")) {
                    realCall = t;
                }
            }
        } catch (Throwable ignored) {
        }

        Class<?> client = loadOrNull(classLoader, "okhttp3.OkHttpClient");
        Class<?> builder = loadOrNull(classLoader, "okhttp3.OkHttpClient$Builder");
        Class<?> exchange = loadOrNull(classLoader, "okhttp3.internal.connection.Exchange");
        if (realCall == null) {
            realCall = loadOrNull(classLoader, "okhttp3.internal.connection.RealCall");
        }
        if (interceptor == null) {
            interceptor = loadOrNull(classLoader, "okhttp3.Interceptor");
        }
        Class<?> chain = loadOrNull(classLoader, "okhttp3.Interceptor$Chain");

        boolean shaded = (client == null || realCall == null || exchange == null);
        return new DiscoveredOkHttp(
                client, builder, realCall, exchange, interceptor, chain,
                request, response, ric, shaded);
    }

    private static Class<?> loadOrNull(ClassLoader classLoader, String fqn) {
        try {
            return classLoader.loadClass(fqn);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String fmt, Object... args) {
        try {
            NetTap.getXposedLogger().log(fmt, args);
        } catch (Throwable ignored) {
        }
    }
}
