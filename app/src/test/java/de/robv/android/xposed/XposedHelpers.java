package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            ClassLoader loader = classLoader == null ? XposedHelpers.class.getClassLoader() : classLoader;
            return Class.forName(className, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = callback(parameterTypesAndCallback);
        Method method = findMethod(clazz, methodName, parameterTypesAndCallback);
        return XposedBridge.hookMethod(method, callback);
    }

    public static Object callMethod(Object target, String methodName, Object... args) {
        if (target == null) {
            throw new NullPointerException("target == null");
        }
        Method method = findMethodByArgs(target.getClass(), methodName, args);
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getObjectField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setObjectField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static XC_MethodHook callback(Object[] values) {
        if (values.length == 0 || !(values[values.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("last argument must be XC_MethodHook");
        }
        return (XC_MethodHook) values[values.length - 1];
    }

    private static Method findMethod(
            Class<?> clazz, String methodName, Object[] parameterTypesAndCallback) {
        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object parameter = parameterTypesAndCallback[i];
            if (parameter instanceof Class<?>) {
                parameterTypes[i] = (Class<?>) parameter;
            } else if (parameter instanceof String) {
                parameterTypes[i] = findClassIfExists((String) parameter, clazz.getClassLoader());
            } else {
                throw new IllegalArgumentException("unsupported parameter token " + parameter);
            }
        }
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("method not found: " + methodName);
    }

    private static Method findMethodByArgs(Class<?> clazz, String methodName, Object[] args) {
        Class<?> current = clazz;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) {
                    continue;
                }
                boolean compatible = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (args[i] == null) {
                        compatible &= !parameterTypes[i].isPrimitive();
                    } else {
                        compatible &= wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass());
                    }
                }
                if (compatible) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalArgumentException("method not found: " + methodName);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return Void.class;
    }
}
