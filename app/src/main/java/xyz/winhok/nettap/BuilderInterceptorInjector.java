package xyz.winhok.nettap;

import java.lang.reflect.InvocationHandler;
import java.util.Collections;
import java.util.WeakHashMap;

final class BuilderInterceptorInjector {
    private static final java.util.Map<Object, Boolean> INJECTED_BUILDERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BuilderInterceptorInjector() {
    }

    static boolean injectOnce(
            Object builder,
            Class<?> interceptorClass,
            ClassLoader classLoader,
            InvocationHandler handler
    ) throws ReflectiveOperationException {
        if (builder == null) {
            return false;
        }

        synchronized (INJECTED_BUILDERS) {
            if (INJECTED_BUILDERS.get(builder) == Boolean.TRUE) {
                return false;
            }

            Object interceptor = java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[] {interceptorClass},
                    handler
            );
            builder.getClass()
                    .getMethod("addInterceptor", interceptorClass)
                    .invoke(builder, interceptor);
            INJECTED_BUILDERS.put(builder, Boolean.TRUE);
            return true;
        }
    }
}
