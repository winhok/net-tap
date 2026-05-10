package xyz.winhok.nettap;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks Fuel's HTTP client. Fuel routes through a {@code Client} interface
 * whose default implementation is {@code HttpClient} — hooking
 * {@code executeRequest(Request)} grabs every Fuel request regardless of
 * whether the app swapped the client (OkHttp-backed, JDK-backed, etc.).
 */
public final class FuelHook {

    private static final String HOOK_NAME = "Fuel";
    private static final String ID_PREFIX = "fuel";
    private static final String HTTP_CLIENT = "com.github.kittinunf.fuel.toolbox.HttpClient";

    private FuelHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("fuel", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("fuel install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        Class<?> client = XposedHelpers.findClassIfExists(HTTP_CLIENT, classLoader);
        if (client == null) {
            throw new RuntimeException("fuel-absent");
        }
        int hooked = 0;
        for (Method m : client.getDeclaredMethods()) {
            if ("executeRequest".equals(m.getName()) && m.getParameterTypes().length == 1) {
                try {
                    XposedBridge.hookMethod(m, new ExecuteRequestHook(packageName));
                    hooked++;
                } catch (Throwable e) {
                    NetTap.getXposedLogger().log("fuel executeRequest hook failed: %s", e);
                }
            }
        }
        if (hooked == 0) {
            throw new RuntimeException("fuel-no-executeRequest");
        }
        NetTap.getXposedLogger().log(
                "installed hook: %s.executeRequest (%d overloads)", HTTP_CLIENT, hooked);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_FUEL);
        } catch (Throwable ignored) {
        }
    }

    private static final class ExecuteRequestHook extends XC_MethodHook {
        private final String packageName;

        ExecuteRequestHook(String packageName) {
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
                String url = "";
                String method = "";
                LinkedHashMap<String, String> reqHeaders = new LinkedHashMap<>();
                CaptureBody reqBody = CaptureBody.omitted(null, -1L, null, "no request body");
                if (request != null) {
                    try {
                        Object u = XposedHelpers.callMethod(request, "getUrl");
                        if (u != null) {
                            url = u.toString();
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        Object m = XposedHelpers.callMethod(request, "getMethod");
                        if (m != null) {
                            method = m.toString();
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        reqHeaders = fuelHeadersToMap(XposedHelpers.callMethod(request, "getHeaders"));
                    } catch (Throwable ignored) {
                    }
                    reqBody = fuelRequestBody(request);
                }

                int responseCode = 0;
                String responseMessage = "";
                LinkedHashMap<String, String> resHeaders = new LinkedHashMap<>();
                CaptureBody resBody = CaptureBody.omitted(null, -1L, null, "no response body observed");
                String error = null;

                Throwable thrown = param.getThrowable();
                if (thrown != null) {
                    error = thrown.toString();
                } else {
                    Object response = param.getResult();
                    if (response != null) {
                        try {
                            Object c = XposedHelpers.callMethod(response, "getStatusCode");
                            if (c instanceof Integer) {
                                responseCode = (Integer) c;
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            Object msg = XposedHelpers.callMethod(response, "getResponseMessage");
                            if (msg != null) {
                                responseMessage = msg.toString();
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            resHeaders = fuelHeadersToMap(
                                    XposedHelpers.callMethod(response, "getHeaders"));
                        } catch (Throwable ignored) {
                        }
                        resBody = fuelResponseBody(response);
                    }
                }

                CaptureEvent event = CaptureEvent.fromParsed(
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
                    MetricsReporter.incCaptured(packageName, MetricsReporter.LAYER_FUEL);
                } catch (Throwable ignored) {
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("fuel after failed: %s", e);
            }
        }
    }

    /** Fuel's Headers extends HashMap<String, HeaderValues extends Collection<String>>. */
    private static LinkedHashMap<String, String> fuelHeadersToMap(Object headers) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (!(headers instanceof Map)) {
            return out;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) headers).entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            Object v = e.getValue();
            String value;
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
                value = sb.toString();
            } else {
                value = v == null ? "" : v.toString();
            }
            out.put(e.getKey().toString(), value);
        }
        return out;
    }

    private static CaptureBody fuelRequestBody(Object request) {
        byte[] bytes = null;
        try {
            Object body = XposedHelpers.callMethod(request, "getBody");
            if (body != null) {
                Object arr = XposedHelpers.callMethod(body, "toByteArray");
                if (arr instanceof byte[]) {
                    bytes = (byte[]) arr;
                }
            }
        } catch (Throwable ignored) {
        }
        return CaptureBody.fromCappedBytes(bytes, CaptureConfig.MAX_BODY_BYTES, "no request body");
    }

    private static CaptureBody fuelResponseBody(Object response) {
        byte[] bytes = null;
        try {
            Object d = XposedHelpers.callMethod(response, "getData");
            if (d instanceof byte[]) {
                bytes = (byte[]) d;
            }
        } catch (Throwable ignored) {
        }
        return CaptureBody.fromCappedBytes(
                bytes, CaptureConfig.MAX_BODY_BYTES, "no response body observed");
    }
}
