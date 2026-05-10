package xyz.winhok.nettap;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks {@link HttpURLConnection} so SDK traffic that uses
 * {@code URL.openConnection()} (Firebase, GMS, Facebook SDK, legacy libraries)
 * gets captured even when the app doesn't use OkHttp or Cronet. Request and
 * response bodies are observed via tee streams wrapped around
 * {@link HttpURLConnection#getOutputStream()} / {@code getInputStream()}.
 */
public final class HttpURLConnectionHook {

    private static final String HOOK_NAME = "HttpURLConnection";
    private static final String ID_PREFIX = "hurl";
    private static final Map<Object, HookState> STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, HookState>());

    private HttpURLConnectionHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        try {
            return InstallGuard.installOncePerLoader(
                    "hurl",
                    classLoader == null ? HttpURLConnection.class.getClassLoader() : classLoader,
                    () -> installInner(packageName));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName) {
        Class<?> http = HttpURLConnection.class;
        int hooked = 0;
        try {
            XposedBridge.hookAllMethods(http, "connect", new ConnectHook(packageName));
            hooked++;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl connect hook failed: %s", e);
        }
        try {
            XposedBridge.hookAllMethods(http, "getOutputStream", new GetOutputStreamHook(packageName));
            hooked++;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl getOutputStream hook failed: %s", e);
        }
        try {
            XposedBridge.hookAllMethods(http, "getInputStream", new GetInputStreamHook(packageName));
            hooked++;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl getInputStream hook failed: %s", e);
        }
        try {
            XposedBridge.hookAllMethods(http, "getErrorStream", new GetErrorStreamHook(packageName));
            hooked++;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl getErrorStream hook failed: %s", e);
        }
        try {
            XposedBridge.hookAllMethods(http, "getResponseCode", new GetResponseCodeHook());
            hooked++;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl getResponseCode hook failed: %s", e);
        }
        try {
            XposedBridge.hookAllMethods(http, "disconnect", new DisconnectHook());
            hooked++;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl disconnect hook failed: %s", e);
        }
        if (hooked == 0) {
            throw new RuntimeException("hurl-no-methods");
        }
        NetTap.getXposedLogger().log(
                "installed hook: java.net.HttpURLConnection (%d methods)", hooked);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_HURL);
        } catch (Throwable ignored) {
        }
    }

    private static HookState stateFor(Object conn, String packageName) {
        if (conn == null) {
            return null;
        }
        HookState state = STATES.get(conn);
        if (state != null) {
            return state;
        }
        synchronized (STATES) {
            state = STATES.get(conn);
            if (state == null) {
                state = new HookState(packageName);
                STATES.put(conn, state);
            }
        }
        return state;
    }

    private static final class ConnectHook extends XC_MethodHook {
        private final String packageName;

        ConnectHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            HookState state = stateFor(param.thisObject, packageName);
            if (state == null) {
                return;
            }
            try {
                Object connection = param.thisObject;
                Object urlObj = XposedHelpers.callMethod(connection, "getURL");
                if (urlObj instanceof URL) {
                    state.url = urlObj.toString();
                }
                state.method = String.valueOf(XposedHelpers.callMethod(connection, "getRequestMethod"));
                Object props = XposedHelpers.callMethod(connection, "getRequestProperties");
                state.requestHeaders.putAll(flattenHeaderMap(props));
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class GetOutputStreamHook extends XC_MethodHook {
        private final String packageName;

        GetOutputStreamHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.getThrowable() != null || !(param.getResult() instanceof OutputStream)) {
                return;
            }
            HookState state = stateFor(param.thisObject, packageName);
            if (state == null) {
                return;
            }
            OutputStream original = (OutputStream) param.getResult();
            if (original instanceof TeeOutputStream) {
                return;
            }
            try {
                TeeOutputStream tee = new TeeOutputStream(original, CaptureConfig.MAX_BODY_BYTES);
                state.requestTee = tee;
                param.setResult(tee);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class GetInputStreamHook extends XC_MethodHook {
        private final String packageName;

        GetInputStreamHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            wrapInputStream(param, packageName);
        }
    }

    private static final class GetErrorStreamHook extends XC_MethodHook {
        private final String packageName;

        GetErrorStreamHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            wrapInputStream(param, packageName);
        }
    }

    private static void wrapInputStream(
            XC_MethodHook.MethodHookParam param,
            String packageName
    ) {
        if (param.getThrowable() != null || !(param.getResult() instanceof InputStream)) {
            return;
        }
        HookState state = stateFor(param.thisObject, packageName);
        if (state == null) {
            return;
        }
        InputStream original = (InputStream) param.getResult();
        if (original instanceof TeeInputStream) {
            return;
        }
        try {
            TeeInputStream tee = new TeeInputStream(
                    original,
                    CaptureConfig.MAX_BODY_BYTES,
                    (bytes, truncated, total) -> recordIfNotRecorded(state, param.thisObject, null));
            state.responseTee = tee;
            param.setResult(tee);
        } catch (Throwable ignored) {
        }
    }

    private static final class GetResponseCodeHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.getThrowable() != null) {
                return;
            }
            HookState state = STATES.get(param.thisObject);
            if (state == null) {
                return;
            }
            try {
                Object r = param.getResult();
                if (r instanceof Integer) {
                    state.responseCode = (Integer) r;
                }
                state.responseMessage = String.valueOf(
                        XposedHelpers.callMethod(param.thisObject, "getResponseMessage"));
                Object headers = XposedHelpers.callMethod(param.thisObject, "getHeaderFields");
                state.responseHeaders.putAll(flattenHeaderMap(headers));
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class DisconnectHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            HookState state = STATES.get(param.thisObject);
            recordIfNotRecorded(state, param.thisObject, null);
        }
    }

    private static void recordIfNotRecorded(HookState state, Object conn, String error) {
        if (state == null) {
            return;
        }
        CaptureEvent event;
        try {
            synchronized (state) {
                if (state.recorded) {
                    return;
                }
                state.recorded = true;
                CaptureBody reqBody = CaptureBody.fromBytes(
                        state.requestTee == null ? null : state.requestTee.capturedBytes(),
                        state.requestTee == null ? 0L : state.requestTee.totalObserved(),
                        state.requestTee != null && state.requestTee.isTruncated(),
                        "no request body");
                CaptureBody resBody = CaptureBody.fromBytes(
                        state.responseTee == null ? null : state.responseTee.capturedBytes(),
                        state.responseTee == null ? 0L : state.responseTee.totalObserved(),
                        state.responseTee != null && state.responseTee.isTruncated(),
                        "no response body observed");
                event = CaptureEvent.complete(
                        CaptureEvent.nextId(ID_PREFIX),
                        CaptureEvent.timestampNow(),
                        state.packageName,
                        HOOK_NAME,
                        state.method,
                        state.url,
                        state.requestHeaders,
                        reqBody,
                        state.responseCode,
                        state.responseMessage == null ? "" : state.responseMessage,
                        state.responseHeaders,
                        resBody,
                        CaptureEvent.elapsedMsSince(state.startedNanos),
                        error
                );
                state.requestTee = null;
                state.responseTee = null;
            }
            CaptureRecorder.record(event);
            try {
                MetricsReporter.incCaptured(state.packageName, MetricsReporter.LAYER_HURL);
            } catch (Throwable ignored) {
            }
        } finally {
            STATES.remove(conn);
        }
    }

    private static LinkedHashMap<String, String> flattenHeaderMap(Object raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (!(raw instanceof Map)) {
            return out;
        }
        for (Object e : ((Map<?, ?>) raw).entrySet()) {
            Map.Entry<?, ?> me = (Map.Entry<?, ?>) e;
            Object k = me.getKey();
            if (k == null) {
                continue;
            }
            Object v = me.getValue();
            String value;
            if (v instanceof List) {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object item : (List<?>) v) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(item == null ? "" : item.toString());
                    first = false;
                }
                value = sb.toString();
            } else {
                value = v == null ? "" : v.toString();
            }
            out.put(String.valueOf(k), value);
        }
        return out;
    }

    private static final class HookState {
        final String packageName;
        final long startedNanos = System.nanoTime();
        String url;
        String method;
        LinkedHashMap<String, String> requestHeaders = new LinkedHashMap<>();
        LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
        int responseCode;
        String responseMessage;
        TeeOutputStream requestTee;
        TeeInputStream responseTee;
        boolean recorded;

        HookState(String packageName) {
            this.packageName = packageName;
        }
    }
}
