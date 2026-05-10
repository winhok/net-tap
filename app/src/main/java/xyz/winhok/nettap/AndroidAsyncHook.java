package xyz.winhok.nettap;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks {@code com.koushikdutta.async.http.AsyncHttpClient.execute}. Captures
 * request metadata at call time and swaps the {@code HttpConnectCallback} with
 * a proxy so the response (status, headers) is recorded when the connection
 * completes. Body capture is metadata-only — the async data pipeline wraps
 * {@code DataCallback}, which is deferred to future work.
 */
public final class AndroidAsyncHook {

    private static final String HOOK_NAME = "AndroidAsync";
    private static final String ID_PREFIX = "android-async";
    private static final String CLIENT = "com.koushikdutta.async.http.AsyncHttpClient";
    private static final String REQUEST = "com.koushikdutta.async.http.AsyncHttpRequest";
    private static final String CONNECT_CALLBACK =
            "com.koushikdutta.async.http.callback.HttpConnectCallback";

    private AndroidAsyncHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("android-async", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("android-async install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        Class<?> client = XposedHelpers.findClassIfExists(CLIENT, classLoader);
        if (client == null) {
            throw new RuntimeException("android-async-absent");
        }
        Class<?> requestCls = XposedHelpers.findClassIfExists(REQUEST, classLoader);
        Class<?> callbackCls = XposedHelpers.findClassIfExists(CONNECT_CALLBACK, classLoader);
        if (requestCls == null || callbackCls == null) {
            throw new RuntimeException("android-async-types-missing");
        }
        int hooked = 0;
        for (Method m : client.getDeclaredMethods()) {
            if (!"execute".equals(m.getName())) {
                continue;
            }
            boolean acceptsRequest = false;
            for (Class<?> pc : m.getParameterTypes()) {
                if (requestCls.isAssignableFrom(pc)) {
                    acceptsRequest = true;
                    break;
                }
            }
            if (!acceptsRequest) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new ExecuteHook(packageName, callbackCls));
                hooked++;
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("android-async execute hook failed: %s", e);
            }
        }
        if (hooked == 0) {
            throw new RuntimeException("android-async-no-execute");
        }
        NetTap.getXposedLogger().log(
                "installed hook: %s.execute (%d overloads)", CLIENT, hooked);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_ANDROID_ASYNC);
        } catch (Throwable ignored) {
        }
    }

    private static final class ExecuteHook extends XC_MethodHook {
        private final String packageName;
        private final Class<?> callbackClass;

        ExecuteHook(String packageName, Class<?> callbackClass) {
            this.packageName = packageName;
            this.callbackClass = callbackClass;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                long startedNanos = System.nanoTime();
                Object request = null;
                Object callback = null;
                int callbackIdx = -1;
                if (param.args != null) {
                    for (int i = 0; i < param.args.length; i++) {
                        Object a = param.args[i];
                        if (a == null) {
                            continue;
                        }
                        if (request == null && isInstanceOfByName(a, REQUEST)) {
                            request = a;
                        } else if (callback == null && callbackClass.isInstance(a)) {
                            callback = a;
                            callbackIdx = i;
                        }
                    }
                }
                if (request == null) {
                    return;
                }
                RequestSnapshot snap = captureRequest(request, startedNanos);
                if (callbackIdx >= 0) {
                    Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                            callbackClass.getClassLoader(),
                            new Class<?>[]{callbackClass},
                            new CallbackProxy(callback, snap, packageName));
                    param.args[callbackIdx] = proxy;
                } else {
                    // Fire-and-forget path — emit a request-only breadcrumb so the
                    // event still shows up in the jsonl.
                    recordRequestOnly(snap, packageName, "android-async no callback");
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("android-async before failed: %s", e);
            }
        }
    }

    private static boolean isInstanceOfByName(Object o, String typeName) {
        if (o == null) {
            return false;
        }
        Class<?> c = o.getClass();
        while (c != null) {
            if (typeName.equals(c.getName())) {
                return true;
            }
            for (Class<?> i : c.getInterfaces()) {
                if (typeName.equals(i.getName())) {
                    return true;
                }
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static RequestSnapshot captureRequest(Object request, long startedNanos) {
        RequestSnapshot s = new RequestSnapshot();
        s.startedNanos = startedNanos;
        try {
            Object uri = XposedHelpers.callMethod(request, "getUri");
            if (uri != null) {
                s.url = uri.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object method = XposedHelpers.callMethod(request, "getMethod");
            if (method != null) {
                s.method = method.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object headers = XposedHelpers.callMethod(request, "getHeaders");
            s.requestHeaders = androidAsyncHeadersToMap(headers);
        } catch (Throwable ignored) {
        }
        return s;
    }

    private static LinkedHashMap<String, String> androidAsyncHeadersToMap(Object headers) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        try {
            Object map = XposedHelpers.callMethod(headers, "getMultiMap");
            if (map instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) map).entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    Object v = e.getValue();
                    if (v instanceof Collection) {
                        StringBuilder sb = new StringBuilder();
                        boolean first = true;
                        for (Object item : (Collection<?>) v) {
                            if (!first) {
                                sb.append(',');
                            }
                            sb.append(item == null ? "" : item.toString());
                            first = false;
                        }
                        out.put(e.getKey().toString(), sb.toString());
                    } else {
                        out.put(e.getKey().toString(), v == null ? "" : v.toString());
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void recordRequestOnly(RequestSnapshot snap, String packageName, String error) {
        CaptureEvent event = CaptureEvent.complete(
                CaptureEvent.nextId(ID_PREFIX),
                CaptureEvent.timestampNow(),
                packageName,
                HOOK_NAME,
                snap.method,
                snap.url,
                snap.requestHeaders,
                CaptureBody.omitted(null, -1L, null, "no request body captured"),
                0,
                "",
                new LinkedHashMap<>(),
                CaptureBody.omitted(null, -1L, null, "no response body observed"),
                CaptureEvent.elapsedMsSince(snap.startedNanos),
                error
        );
        CaptureRecorder.record(event);
        try {
            MetricsReporter.incCaptured(packageName, MetricsReporter.LAYER_ANDROID_ASYNC);
        } catch (Throwable ignored) {
        }
    }

    private static final class RequestSnapshot {
        long startedNanos;
        String url = "";
        String method = "";
        LinkedHashMap<String, String> requestHeaders = new LinkedHashMap<>();
    }

    private static final class CallbackProxy implements java.lang.reflect.InvocationHandler {
        private final Object delegate;
        private final RequestSnapshot snap;
        private final String packageName;

        CallbackProxy(Object delegate, RequestSnapshot snap, String packageName) {
            this.delegate = delegate;
            this.snap = snap;
            this.packageName = packageName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("onConnectCompleted".equals(method.getName()) && args != null && args.length >= 2) {
                try {
                    observeResponse(args[0], args[1]);
                } catch (Throwable e) {
                    NetTap.getXposedLogger().log("android-async observe failed: %s", e);
                }
            }
            if (delegate == null) {
                return null;
            }
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() == null ? e : e.getCause();
            }
        }

        private void observeResponse(Object error, Object response) {
            int code = 0;
            String message = "";
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            String err = error == null ? null : error.toString();
            if (response != null) {
                try {
                    Object c = XposedHelpers.callMethod(response, "code");
                    if (c instanceof Integer) {
                        code = (Integer) c;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    Object m = XposedHelpers.callMethod(response, "message");
                    if (m != null) {
                        message = m.toString();
                    }
                } catch (Throwable ignored) {
                }
                try {
                    Object h = XposedHelpers.callMethod(response, "headers");
                    headers = androidAsyncHeadersToMap(h);
                } catch (Throwable ignored) {
                }
            }
            CaptureEvent event = CaptureEvent.complete(
                    CaptureEvent.nextId(ID_PREFIX),
                    CaptureEvent.timestampNow(),
                    packageName,
                    HOOK_NAME,
                    snap.method,
                    snap.url,
                    snap.requestHeaders,
                    CaptureBody.omitted(null, -1L, null, "no request body captured"),
                    code,
                    message,
                    headers,
                    CaptureBody.omitted(null, -1L, null,
                            "android-async response body not observed"),
                    CaptureEvent.elapsedMsSince(snap.startedNanos),
                    err
            );
            CaptureRecorder.record(event);
            try {
                MetricsReporter.incCaptured(packageName, MetricsReporter.LAYER_ANDROID_ASYNC);
            } catch (Throwable ignored) {
            }
        }
    }
}
