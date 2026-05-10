package xyz.winhok.nettap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks Volley's {@code BasicNetwork.performRequest(Request)}. This one method
 * sits above every HttpStack Volley ships (Hurl, OkHttp, Apache) so we get
 * request+response+timing in a single capture without double-counting the
 * lower layers.
 */
public final class VolleyHook {

    private static final String HOOK_NAME = "Volley";
    private static final String ID_PREFIX = "volley";
    private static final String BASIC_NETWORK = "com.android.volley.toolbox.BasicNetwork";
    private static final String REQUEST = "com.android.volley.Request";

    private VolleyHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("volley", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("volley install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        Class<?> network = XposedHelpers.findClassIfExists(BASIC_NETWORK, classLoader);
        if (network == null) {
            throw new RuntimeException("volley-absent");
        }
        Class<?> requestCls = XposedHelpers.findClassIfExists(REQUEST, classLoader);
        if (requestCls == null) {
            throw new RuntimeException("volley-request-absent");
        }
        try {
            XposedHelpers.findAndHookMethod(network, "performRequest", requestCls,
                    new PerformRequestHook(packageName));
        } catch (Throwable e) {
            throw new RuntimeException("volley-hook-failed: " + e);
        }
        NetTap.getXposedLogger().log(
                "installed hook: %s.performRequest(%s)", BASIC_NETWORK, REQUEST);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_VOLLEY);
        } catch (Throwable ignored) {
        }
    }

    private static final class PerformRequestHook extends XC_MethodHook {
        private final String packageName;

        PerformRequestHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            param.setObjectExtra("nettap.startedNanos", System.nanoTime());
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object started = param.getObjectExtra("nettap.startedNanos");
            long startedNanos = started instanceof Long ? (Long) started : System.nanoTime();
            try {
                Object request = param.args == null || param.args.length == 0 ? null : param.args[0];
                if (request == null) {
                    return;
                }
                String url = stringOf(XposedHelpers.callMethod(request, "getUrl"));
                String method = volleyMethodName(XposedHelpers.callMethod(request, "getMethod"));
                LinkedHashMap<String, String> reqHeaders = headersFromRequest(request);
                CaptureBody reqBody = requestBody(request);

                int responseCode = 0;
                LinkedHashMap<String, String> resHeaders = new LinkedHashMap<>();
                CaptureBody resBody = CaptureBody.omitted(
                        null, -1L, null, "no response body observed");
                String error = null;

                Throwable thrown = param.getThrowable();
                if (thrown != null) {
                    error = thrown.toString();
                } else {
                    Object response = param.getResult();
                    if (response != null) {
                        try {
                            Object code = XposedHelpers.getObjectField(response, "statusCode");
                            if (code instanceof Integer) {
                                responseCode = (Integer) code;
                            }
                        } catch (Throwable ignored) {
                        }
                        resHeaders = headersFromResponse(response);
                        resBody = responseBody(response);
                    }
                }

                CaptureEvent event = CaptureEvent.complete(
                        CaptureEvent.nextId(ID_PREFIX),
                        CaptureEvent.timestampNow(),
                        packageName,
                        HOOK_NAME,
                        method,
                        url,
                        reqHeaders,
                        reqBody,
                        responseCode,
                        "",
                        resHeaders,
                        resBody,
                        CaptureEvent.elapsedMsSince(startedNanos),
                        error
                );
                CaptureRecorder.record(event);
                try {
                    MetricsReporter.incCaptured(packageName, MetricsReporter.LAYER_VOLLEY);
                } catch (Throwable ignored) {
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("volley after failed: %s", e);
            }
        }
    }

    private static LinkedHashMap<String, String> headersFromRequest(Object request) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            Object raw = XposedHelpers.callMethod(request, "getHeaders");
            if (raw instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                    if (e.getKey() != null) {
                        out.put(e.getKey().toString(), String.valueOf(e.getValue()));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static LinkedHashMap<String, String> headersFromResponse(Object response) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            Object headers = XposedHelpers.getObjectField(response, "allHeaders");
            if (headers instanceof List) {
                for (Object h : (List<?>) headers) {
                    if (h == null) {
                        continue;
                    }
                    String name = stringOf(XposedHelpers.callMethod(h, "getName"));
                    String value = stringOf(XposedHelpers.callMethod(h, "getValue"));
                    if (name != null) {
                        out.put(name, value == null ? "" : value);
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object headers = XposedHelpers.getObjectField(response, "headers");
            if (headers instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) headers).entrySet()) {
                    if (e.getKey() != null) {
                        out.put(e.getKey().toString(), String.valueOf(e.getValue()));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static CaptureBody requestBody(Object request) {
        byte[] bytes = null;
        try {
            Object b = XposedHelpers.callMethod(request, "getBody");
            if (b instanceof byte[]) {
                bytes = (byte[]) b;
            }
        } catch (Throwable ignored) {
        }
        return CaptureBody.fromCappedBytes(bytes, CaptureConfig.MAX_BODY_BYTES, "no request body");
    }

    private static CaptureBody responseBody(Object response) {
        byte[] bytes = null;
        try {
            Object d = XposedHelpers.getObjectField(response, "data");
            if (d instanceof byte[]) {
                bytes = (byte[]) d;
            }
        } catch (Throwable ignored) {
        }
        return CaptureBody.fromCappedBytes(
                bytes, CaptureConfig.MAX_BODY_BYTES, "no response body observed");
    }

    private static String volleyMethodName(Object method) {
        if (!(method instanceof Integer)) {
            return "";
        }
        switch ((Integer) method) {
            case -1: return "GET-or-POST";
            case 0: return "GET";
            case 1: return "POST";
            case 2: return "PUT";
            case 3: return "DELETE";
            case 4: return "HEAD";
            case 5: return "OPTIONS";
            case 6: return "TRACE";
            case 7: return "PATCH";
            default: return String.valueOf(method);
        }
    }

    private static String stringOf(Object o) {
        return o == null ? null : o.toString();
    }
}
