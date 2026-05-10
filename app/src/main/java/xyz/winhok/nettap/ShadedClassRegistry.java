package xyz.winhok.nettap;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Thread-safe registry holding discovered (possibly shaded) OkHttp class
 * references per ClassLoader. {@link ShadedOkHttpDiscovery} populates this
 * registry; hooks read from it when deciding which classes to target.
 */
public final class ShadedClassRegistry {

    public static final class DiscoveredOkHttp {
        public final Class<?> okHttpClient;
        public final Class<?> okHttpClientBuilder;
        public final Class<?> realCall;
        public final Class<?> exchange;
        public final Class<?> interceptor;
        public final Class<?> chain;
        public final Class<?> request;
        public final Class<?> response;
        public final Class<?> realInterceptorChain;
        public final boolean isShaded;

        public DiscoveredOkHttp(
                Class<?> okHttpClient,
                Class<?> okHttpClientBuilder,
                Class<?> realCall,
                Class<?> exchange,
                Class<?> interceptor,
                Class<?> chain,
                Class<?> request,
                Class<?> response,
                Class<?> realInterceptorChain,
                boolean isShaded
        ) {
            this.okHttpClient = okHttpClient;
            this.okHttpClientBuilder = okHttpClientBuilder;
            this.realCall = realCall;
            this.exchange = exchange;
            this.interceptor = interceptor;
            this.chain = chain;
            this.request = request;
            this.response = response;
            this.realInterceptorChain = realInterceptorChain;
            this.isShaded = isShaded;
        }

        public boolean hasAny() {
            return okHttpClient != null || realCall != null || exchange != null
                    || realInterceptorChain != null;
        }

        @Override
        public String toString() {
            return "DiscoveredOkHttp{shaded=" + isShaded
                    + ", client=" + name(okHttpClient)
                    + ", realCall=" + name(realCall)
                    + ", exchange=" + name(exchange)
                    + ", chain=" + name(realInterceptorChain) + "}";
        }

        private static String name(Class<?> c) {
            return c == null ? "null" : c.getName();
        }
    }

    private static final Map<ClassLoader, DiscoveredOkHttp> REGISTRY =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, DiscoveredOkHttp>());

    private ShadedClassRegistry() {
    }

    public static DiscoveredOkHttp get(ClassLoader loader) {
        if (loader == null) {
            return null;
        }
        return REGISTRY.get(loader);
    }

    public static void put(ClassLoader loader, DiscoveredOkHttp discovered) {
        if (loader == null || discovered == null) {
            return;
        }
        REGISTRY.put(loader, discovered);
    }

    public static void resetForTesting() {
        REGISTRY.clear();
    }
}
