package xyz.winhok.nettap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public final class BuilderInterceptorHook extends XC_MethodHook {
    private static final String HOOK_NAME = "OkHttpClient.Builder.interceptor";
    private static final String ID_PREFIX = "builder-interceptor";

    private final String packageName;
    private final Class<?> interceptorClass;
    private final ClassLoader classLoader;

    public BuilderInterceptorHook(String packageName, Class<?> interceptorClass, ClassLoader classLoader) {
        this.packageName = packageName;
        this.interceptorClass = interceptorClass;
        this.classLoader = classLoader;
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Object builder = param.thisObject;
        if (builder == null) {
            return;
        }

        try {
            boolean injected = BuilderInterceptorInjector.injectOnce(
                    builder,
                    interceptorClass,
                    classLoader,
                    new LoggingInterceptorHandler(packageName)
            );
            if (injected) {
                NetTap.getXposedLogger().log("injected interceptor: %s", HOOK_NAME);
            }
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("failed to inject %s: %s", HOOK_NAME, e);
        }
    }

    private static final class LoggingInterceptorHandler implements InvocationHandler {
        private final String packageName;

        private LoggingInterceptorHandler(String packageName) {
            this.packageName = packageName;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("toString".equals(name)) {
                return "NetTapInterceptor@" + System.identityHashCode(proxy);
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(args != null && args.length > 0 && args[0] == proxy);
            }
            if (!"intercept".equals(name)) {
                return null;
            }

            try {
                return intercept(args == null || args.length == 0 ? null : args[0]);
            } catch (Throwable t) {
                throw throwableForProxyMethod(method, unwrap(t));
            }
        }

        private Object intercept(Object chain) throws Throwable {
            long startedNanos = System.nanoTime();
            Object request = null;
            Object response = null;
            Throwable failure = null;

            try {
                request = XposedHelpers.callMethod(chain, "request");
                String url = ReflectiveOkHttp.url(request);
                NetTap.getXposedLogger().log("%s intercepted: %s", HOOK_NAME, url != null ? url : "(unknown url)");
                response = XposedHelpers.callMethod(chain, "proceed", request);
                return response;
            } catch (Throwable t) {
                failure = unwrap(t);
                throw failure;
            } finally {
                record(request, response, startedNanos, failure);
            }
        }

        private void record(Object request, Object response, long startedNanos, Throwable failure) {
            try {
                Object eventRequest = ReflectiveOkHttp.requestForResponse(response, request);
                CaptureEvent event = CaptureEvent.fromOkHttpCall(
                        CaptureEvent.nextId(ID_PREFIX),
                        CaptureEvent.timestampNow(),
                        packageName,
                        HOOK_NAME,
                        eventRequest,
                        response,
                        CaptureEvent.elapsedMsSince(startedNanos),
                        failure
                );
                CaptureRecorder.record(event);
            } catch (Throwable e) {
                NetTap.getXposedLogger().logSafe("%s capture failed: %s", HOOK_NAME, e);
            }
        }
    }

    private static Throwable throwableForProxyMethod(Method method, Throwable throwable) {
        if (throwable instanceof RuntimeException || throwable instanceof Error) {
            return throwable;
        }
        Class<?>[] exceptionTypes = method.getExceptionTypes();
        for (Class<?> exceptionType : exceptionTypes) {
            if (exceptionType.isAssignableFrom(throwable.getClass())) {
                return throwable;
            }
        }
        return new RuntimeException(throwable);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (true) {
            Throwable cause = null;
            if (current instanceof InvocationTargetException) {
                cause = ((InvocationTargetException) current).getTargetException();
            } else if (current instanceof java.lang.reflect.UndeclaredThrowableException) {
                cause = ((java.lang.reflect.UndeclaredThrowableException) current).getUndeclaredThrowable();
            }
            if (cause == null || cause == current) {
                return current;
            }
            current = cause;
        }
    }
}
