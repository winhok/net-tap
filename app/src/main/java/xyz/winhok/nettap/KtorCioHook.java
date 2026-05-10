package xyz.winhok.nettap;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks Ktor client's CIO engine. Covers pure-Kotlin Ktor traffic that does
 * not route through OkHttp / Apache / HURL. Target is
 * {@code CIOEngine.execute(HttpRequestData)} (a suspend function — its JVM
 * signature carries an extra {@code Continuation} parameter, so we iterate
 * declared methods rather than pin a signature).
 *
 * <p>Response body is skipped because CIO delivers bytes through a single-
 * consumer {@code ByteReadChannel}; draining it would break the caller.
 * Request body is captured when it's {@code ByteArrayContent} or
 * {@code TextContent}.
 */
public final class KtorCioHook {

    private static final String HOOK_NAME = "KtorCIO";
    private static final String ID_PREFIX = "ktor-cio";
    private static final String ENGINE = "io.ktor.client.engine.cio.CIOEngine";

    private KtorCioHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("ktor-cio", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("ktor-cio install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        Class<?> engine = XposedHelpers.findClassIfExists(ENGINE, classLoader);
        if (engine == null) {
            throw new RuntimeException("ktor-cio-absent");
        }
        int hooked = 0;
        for (Method m : engine.getDeclaredMethods()) {
            if ("execute".equals(m.getName())) {
                try {
                    XposedBridge.hookMethod(m, new ExecuteHook(packageName));
                    hooked++;
                } catch (Throwable e) {
                    NetTap.getXposedLogger().log("ktor-cio execute hook failed: %s", e);
                }
            }
        }
        if (hooked == 0) {
            throw new RuntimeException("ktor-cio-no-execute");
        }
        NetTap.getXposedLogger().log(
                "installed hook: %s.execute (%d overloads)", ENGINE, hooked);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_KTOR_CIO);
        } catch (Throwable ignored) {
        }
    }

    private static final class ExecuteHook extends XC_MethodHook {
        private final String packageName;

        ExecuteHook(String packageName) {
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
                Object data = param.args == null || param.args.length == 0 ? null : param.args[0];
                if (data == null) {
                    return;
                }
                String url = ktorUrl(data);
                String method = ktorMethod(data);
                LinkedHashMap<String, String> reqHeaders = ktorHeadersFromRequest(data);
                CaptureBody reqBody = ktorRequestBody(data);

                int responseCode = 0;
                String responseMessage = "";
                LinkedHashMap<String, String> resHeaders = new LinkedHashMap<>();
                CaptureBody resBody = CaptureBody.omitted(
                        null, -1L, null, "ktor-cio response body is a single-consumer channel");
                String error = null;

                Throwable thrown = param.getThrowable();
                if (thrown != null) {
                    error = thrown.toString();
                } else {
                    Object response = param.getResult();
                    if (response != null) {
                        try {
                            Object status = XposedHelpers.callMethod(response, "getStatusCode");
                            if (status != null) {
                                Object code = XposedHelpers.callMethod(status, "getValue");
                                if (code instanceof Integer) {
                                    responseCode = (Integer) code;
                                }
                                Object desc = XposedHelpers.callMethod(status, "getDescription");
                                if (desc != null) {
                                    responseMessage = desc.toString();
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            resHeaders = ktorHeadersToMap(
                                    XposedHelpers.callMethod(response, "getHeaders"));
                        } catch (Throwable ignored) {
                        }
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
                        responseMessage,
                        resHeaders,
                        resBody,
                        CaptureEvent.elapsedMsSince(startedNanos),
                        error
                );
                CaptureRecorder.record(event);
                try {
                    MetricsReporter.incCaptured(packageName, MetricsReporter.LAYER_KTOR_CIO);
                } catch (Throwable ignored) {
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("ktor-cio after failed: %s", e);
            }
        }
    }

    private static String ktorUrl(Object requestData) {
        try {
            Object url = XposedHelpers.callMethod(requestData, "getUrl");
            if (url != null) {
                return url.toString();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String ktorMethod(Object requestData) {
        try {
            Object m = XposedHelpers.callMethod(requestData, "getMethod");
            if (m != null) {
                Object v = XposedHelpers.callMethod(m, "getValue");
                if (v != null) {
                    return v.toString();
                }
                return m.toString();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static LinkedHashMap<String, String> ktorHeadersFromRequest(Object requestData) {
        try {
            Object h = XposedHelpers.callMethod(requestData, "getHeaders");
            return ktorHeadersToMap(h);
        } catch (Throwable ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static LinkedHashMap<String, String> ktorHeadersToMap(Object headers) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        try {
            Object entries = XposedHelpers.callMethod(headers, "entries");
            if (entries instanceof Iterable) {
                for (Object e : (Iterable<?>) entries) {
                    if (!(e instanceof Map.Entry)) {
                        continue;
                    }
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) e;
                    Object k = entry.getKey();
                    if (k == null) {
                        continue;
                    }
                    Object v = entry.getValue();
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
                    out.put(k.toString(), value);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static CaptureBody ktorRequestBody(Object requestData) {
        Object body;
        try {
            body = XposedHelpers.callMethod(requestData, "getBody");
        } catch (Throwable e) {
            return CaptureBody.omitted(null, -1L, null, "no request body");
        }
        if (body == null) {
            return CaptureBody.omitted(null, -1L, null, "no request body");
        }
        String cls = body.getClass().getName();
        if (cls.contains("ByteArrayContent")) {
            try {
                Object arr = XposedHelpers.callMethod(body, "bytes");
                if (arr instanceof byte[]) {
                    return CaptureBody.fromCappedBytes(
                            (byte[]) arr, CaptureConfig.MAX_BODY_BYTES, "no request body");
                }
            } catch (Throwable ignored) {
            }
        }
        if (cls.contains("TextContent")) {
            try {
                Object text = XposedHelpers.callMethod(body, "getText");
                if (text != null) {
                    byte[] bytes = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return CaptureBody.fromCappedBytes(
                            bytes, CaptureConfig.MAX_BODY_BYTES, "no request body");
                }
            } catch (Throwable ignored) {
            }
        }
        return CaptureBody.omitted(null, -1L, null, "ktor-cio body type unsupported: " + cls);
    }
}
