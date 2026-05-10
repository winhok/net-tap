package xyz.winhok.nettap;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-{@link Class} no-arg method cache. Hot hook paths call
 * {@link Reflect#findMethod} repeatedly for the same few names
 * ({@code url}, {@code method}, {@code code}, ...); the
 * {@link ClassValue} keeps resolved {@link Method}s alive with their owning
 * {@code Class} and negative hits are remembered so R8-minified targets
 * don't pay the full scan + exception cost on every call.
 */
public final class ReflectCache {

    private static final Object MISSING = new Object();

    private static final ClassValue<ConcurrentHashMap<String, Object>> METHODS =
            new ClassValue<ConcurrentHashMap<String, Object>>() {
                @Override
                protected ConcurrentHashMap<String, Object> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

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
