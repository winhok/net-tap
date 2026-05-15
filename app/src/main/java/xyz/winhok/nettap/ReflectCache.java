package xyz.winhok.nettap;

import android.annotation.SuppressLint;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-{@link Class} no-arg method cache. Hot hook paths call
 * {@link Reflect#findMethod} repeatedly for the same few names
 * ({@code url}, {@code method}, {@code code}, ...); the cache keeps resolved
 * {@link Method}s alive with their owning {@code Class} and remembers negative
 * hits so R8-minified targets don't pay the full scan + exception cost on
 * every call. Uses {@link ClassValue} on API 34+ and a plain map elsewhere.
 */
public final class ReflectCache {

    private static final Object MISSING = new Object();

    private static final PerClassCache METHODS =
            Build.VERSION.SDK_INT >= 34 ? new ClassValueCache() : new MapCache();

    private interface PerClassCache {
        ConcurrentHashMap<String, Object> get(Class<?> owner);
    }

    @SuppressLint("NewApi")
    private static final class ClassValueCache implements PerClassCache {
        private final ClassValue<ConcurrentHashMap<String, Object>> cv =
                new ClassValue<>() {
                    @Override
                    protected ConcurrentHashMap<String, Object> computeValue(Class<?> type) {
                        return new ConcurrentHashMap<>();
                    }
                };

        @Override
        public ConcurrentHashMap<String, Object> get(Class<?> owner) {
            return cv.get(owner);
        }
    }

    private static final class MapCache implements PerClassCache {
        private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> map =
                new ConcurrentHashMap<>();

        @Override
        public ConcurrentHashMap<String, Object> get(Class<?> owner) {
            return map.computeIfAbsent(owner, k -> new ConcurrentHashMap<>());
        }
    }

    private ReflectCache() {
    }

    public static Method noArgMethod(Class<?> owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        ConcurrentHashMap<String, Object> cache = METHODS.get(owner);
        Object cached = cache.get(name);
        if (cached == MISSING) {
            return null;
        }
        if (cached instanceof Method) {
            return (Method) cached;
        }
        Method resolved = Reflect.findMethod(owner, name);
        cache.putIfAbsent(name, resolved == null ? MISSING : resolved);
        return resolved;
    }
}
