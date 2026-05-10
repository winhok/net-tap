package xyz.winhok.nettap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public final class BuilderInterceptorHook extends XC_MethodHook {
    private static final String HOOK_NAME = "OkHttpClient.Builder.interceptor";
    private static final AtomicLong NEXT_ID = new AtomicLong();

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
                Object eventRequest = requestFor(response, request);
                CaptureEvent event = CaptureEvent.complete(
                        nextId(),
                        timestamp(),
                        packageName,
                        HOOK_NAME,
                        ReflectiveOkHttp.method(eventRequest),
                        ReflectiveOkHttp.url(eventRequest),
                        ReflectiveOkHttp.headers(eventRequest),
                        ReflectiveOkHttp.requestBody(eventRequest),
                        response == null ? 0L : ReflectiveOkHttp.responseCode(response),
                        response == null ? "" : ReflectiveOkHttp.responseMessage(response),
                        response == null ? new LinkedHashMap<String, String>() : ReflectiveOkHttp.headers(response),
                        response == null
                                ? CaptureBody.omitted(null, -1L, null, "response unavailable")
                                : ReflectiveOkHttp.responseBody(response),
                        durationMs(startedNanos),
                        failure == null ? null : String.valueOf(failure)
                );
                CaptureRecorder.record(event);
            } catch (Throwable e) {
                try {
                    NetTap.getXposedLogger().log("%s capture failed: %s", HOOK_NAME, e);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Object requestFor(Object response, Object fallback) {
        Object responseRequest = ReflectiveOkHttp.requestFromResponse(response);
        if (responseRequest != null) {
            return responseRequest;
        }
        return fallback;
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

    private static long durationMs(long startedNanos) {
        long elapsedNanos = System.nanoTime() - startedNanos;
        if (elapsedNanos <= 0L) {
            return 0L;
        }
        return elapsedNanos / 1_000_000L;
    }

    private static String nextId() {
        return "builder-interceptor-" + NEXT_ID.incrementAndGet();
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(System.currentTimeMillis()));
    }
}
