package xyz.winhok.nettap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Reflection helpers for resolving methods and fields on R8/ProGuard-obfuscated
 * classes (OkHttp, okio, Cronet, etc.) by structural signature rather than name.
 *
 * <p>All lookups walk the declared superclass chain up to {@link Object}.
 * Public methods from super-interfaces are reachable via the initial
 * {@link Class#getMethod} probe; non-public interface methods (e.g.
 * package-private default methods) are not searched.
 */
public final class Reflect {

    private static final Class<?>[] EMPTY_TYPES = new Class<?>[0];
    private static final String[] DEFAULT_READ_NAMES = {"read"};

    private Reflect() {
    }

    /**
     * Finds a method with the exact name and parameter types. Tries the public
     * inherited surface first, then walks declared methods up the superclass
     * chain. Returns {@code null} if no match exists.
     */
    public static Method findMethod(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        if (declaringClass == null || name == null) {
            return null;
        }
        Class<?>[] params = parameterTypes == null ? EMPTY_TYPES : parameterTypes;
        try {
            Method m = declaringClass.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
        }
        Class<?> current = declaringClass;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Finds a method by return-type and parameter-type signature, ignoring the
     * method name. {@code returnType == null} matches any return type.
     * Primitive and wrapper types (e.g. {@code long} and {@code Long}) are
     * treated as equivalent for both return type and parameter matching.
     * Returns the first match in iteration order; {@code null} if none.
     *
     * <p><b>Stability note:</b> When multiple methods match the signature, the
     * returned method is implementation-defined: it depends on the order of
     * {@link Class#getMethods()} and {@link Class#getDeclaredMethods()}, which
     * the JLS does not specify. Callers that need stability across JVMs should
     * narrow the search by name (see {@link #findMethod}) or by parameter type
     * (see {@link #findUnaryMethodAccepting}).
     */
    public static Method findMethodBySignature(Class<?> declaringClass, Class<?> returnType, Class<?>... parameterTypes) {
        if (declaringClass == null) {
            return null;
        }
        Class<?>[] expected = parameterTypes == null ? EMPTY_TYPES : parameterTypes;
        for (Method m : allMethods(declaringClass)) {
            if (returnType != null && !typesEquivalent(returnType, m.getReturnType())) {
                continue;
            }
            Class<?>[] actual = m.getParameterTypes();
            if (actual.length != expected.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < expected.length; i++) {
                if (!typesEquivalent(expected[i], actual[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /**
     * Finds a single-argument method whose declared parameter type can accept
     * {@code argClass}. {@code methodName == null} matches any name.
     */
    public static Method findUnaryMethodAccepting(Class<?> declaringClass, String methodName, Class<?> argClass) {
        if (declaringClass == null || argClass == null) {
            return null;
        }
        for (Method m : allMethods(declaringClass)) {
            if (methodName != null && !methodName.equals(m.getName())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && params[0].isAssignableFrom(argClass)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /**
     * Finds an okio-style {@code Source.read(Buffer, long): long} method,
     * trying the canonical name {@code read} first and falling back to any
     * signature-compatible method. {@code long} and {@code Long} are accepted
     * interchangeably for both the second parameter and the return type.
     *
     * <p>This overload is for callers with no obfuscation context. To bias
     * lookup toward additional caller-specific aliases (e.g. R8/ProGuard
     * minified names known to the caller), use
     * {@link #findReadIntoSinkMethod(Class, Class, String...)}.
     */
    public static Method findReadIntoSinkMethod(Class<?> declaringClass, Class<?> bufferClass) {
        return findReadIntoSinkMethod(declaringClass, bufferClass, DEFAULT_READ_NAMES);
    }

    /**
     * Variant of {@link #findReadIntoSinkMethod(Class, Class)} that lets the
     * caller supply additional preferred method names to try before falling
     * back to a name-agnostic, signature-only scan. Names are tried in the
     * order given; if {@code preferredNames} is {@code null} or empty the
     * lookup is purely signature-based.
     */
    public static Method findReadIntoSinkMethod(Class<?> declaringClass, Class<?> bufferClass, String... preferredNames) {
        if (declaringClass == null || bufferClass == null) {
            return null;
        }
        if (preferredNames != null) {
            for (String preferredName : preferredNames) {
                if (preferredName == null) {
                    continue;
                }
                Method m = findReadCandidate(declaringClass, preferredName, bufferClass);
                if (m != null) {
                    return m;
                }
            }
        }
        return findReadCandidate(declaringClass, null, bufferClass);
    }

    /**
     * Finds the first field whose declared type equals {@code fieldType},
     * walking the declared superclass chain up to {@link Object}.
     */
    public static Field findFieldByType(Class<?> declaringClass, Class<?> fieldType) {
        if (declaringClass == null || fieldType == null) {
            return null;
        }
        Class<?> current = declaringClass;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                if (fieldType.equals(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Variant of {@link #findFieldByType} that compares against
     * {@link Class#getName()}. Useful when the target type is not loadable
     * from the host classloader (e.g. {@code android.util.SparseArray}).
     */
    public static Field findFieldByTypeName(Class<?> declaringClass, String typeName) {
        if (declaringClass == null || typeName == null) {
            return null;
        }
        Class<?> current = declaringClass;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                if (typeName.equals(f.getType().getName())) {
                    f.setAccessible(true);
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Reads a static field value, returning {@code null} on any failure.
     * Access uses the supplied {@link Field} directly.
     */
    public static Object getStaticFieldValue(Field field) {
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Invokes a no-argument method on {@code target}, walking superclasses to
     * locate it. Throws {@link NoSuchMethodException} if the method is missing
     * or {@code target} is {@code null}; underlying invocation failures are
     * unwrapped via {@link #invoke(Object, Method, Object...)} so callers see
     * the target's original {@link RuntimeException} or {@link Error} rather
     * than an {@link InvocationTargetException} wrapper.
     *
     * <p>The {@code throws ReflectiveOperationException} declaration is kept
     * for source-compatibility; in practice this method only throws
     * {@link NoSuchMethodException} (the missing-method signal) and unchecked
     * exceptions propagated from the target.
     */
    public static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchMethodException(methodName);
        }
        Method m = findMethod(target.getClass(), methodName);
        if (m == null) {
            throw new NoSuchMethodException(methodName);
        }
        return invoke(target, m);
    }

    /**
     * Invokes {@code method} on {@code target}, unwrapping
     * {@link InvocationTargetException}: a {@link RuntimeException} or
     * {@link Error} cause is rethrown as-is (no double wrapping); any other
     * cause is wrapped into a {@link RuntimeException}.
     */
    public static Object invoke(Object target, Method method, Object... args) {
        if (method == null) {
            throw new IllegalArgumentException("method == null");
        }
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause == null ? e : cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new instance of {@code cls} using its declared no-arg constructor.
     * Constructor accessibility is forced via {@link Constructor#setAccessible(boolean)}.
     *
     * <p>Exception unwrapping mirrors {@link #invoke(Object, Method, Object...)}:
     * the constructor's {@code RuntimeException} or {@code Error} cause is rethrown
     * as-is; checked causes are wrapped in {@code RuntimeException}; underlying
     * {@code ReflectiveOperationException} (no such constructor, illegal access,
     * abstract class) is wrapped in {@code RuntimeException}.
     *
     * @throws IllegalArgumentException if {@code cls} is null
     * @throws RuntimeException on any reflective or constructor failure (see above)
     */
    public static Object newInstance(Class<?> cls) {
        if (cls == null) {
            throw new IllegalArgumentException("cls == null");
        }
        try {
            Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause == null ? e : cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findReadCandidate(Class<?> declaringClass, String name, Class<?> bufferClass) {
        for (Method m : allMethods(declaringClass)) {
            if (name != null && !name.equals(m.getName())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 2) {
                continue;
            }
            if (!params[0].isAssignableFrom(bufferClass)) {
                continue;
            }
            if (!isLongLike(params[1]) || !isLongLike(m.getReturnType())) {
                continue;
            }
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    private static List<Method> allMethods(Class<?> declaringClass) {
        LinkedHashMap<String, Method> seen = new LinkedHashMap<>();
        for (Method m : declaringClass.getMethods()) {
            seen.putIfAbsent(methodKey(m), m);
        }
        Class<?> current = declaringClass;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                seen.putIfAbsent(methodKey(m), m);
            }
            current = current.getSuperclass();
        }
        return new ArrayList<>(seen.values());
    }

    private static String methodKey(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getName());
        }
        return sb.append(')').toString();
    }

    private static boolean typesEquivalent(Class<?> expected, Class<?> actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (expected.isPrimitive()) {
            return primitiveWrapper(expected).equals(actual);
        }
        if (actual.isPrimitive()) {
            return primitiveWrapper(actual).equals(expected);
        }
        return false;
    }

    private static boolean isLongLike(Class<?> type) {
        return type == long.class || type == Long.class;
    }

    private static Class<?> primitiveWrapper(Class<?> primitiveType) {
        if (primitiveType == boolean.class) {
            return Boolean.class;
        }
        if (primitiveType == byte.class) {
            return Byte.class;
        }
        if (primitiveType == char.class) {
            return Character.class;
        }
        if (primitiveType == short.class) {
            return Short.class;
        }
        if (primitiveType == int.class) {
            return Integer.class;
        }
        if (primitiveType == long.class) {
            return Long.class;
        }
        if (primitiveType == float.class) {
            return Float.class;
        }
        if (primitiveType == double.class) {
            return Double.class;
        }
        return Void.class;
    }
}
