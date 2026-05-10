package xyz.winhok.nettap;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks Apache HttpClient 5's {@code CloseableHttpClient.doExecute}. Every
 * sync/async exec chain funnels through this method, so one hook captures
 * the whole client surface.
 *
 * <p>Body capture only reads repeatable entities — streaming bodies are
 * recorded as metadata-only to avoid draining the caller's stream.
 */
public final class ApacheHttp5Hook {

    private static final String HOOK_NAME = "ApacheHttpClient5";
    private static final String ID_PREFIX = "apache5";
    private static final String CLIENT = "org.apache.hc.client5.http.impl.classic.CloseableHttpClient";

    private ApacheHttp5Hook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("apache5", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("apache5 install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        Class<?> client = XposedHelpers.findClassIfExists(CLIENT, classLoader);
        if (client == null) {
            throw new RuntimeException("apache5-absent");
        }
        int hooked = 0;
        for (Method m : client.getDeclaredMethods()) {
            if (!"doExecute".equals(m.getName())) {
                continue;
            }
            if (m.getParameterTypes().length < 2) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new DoExecuteHook(packageName));
                hooked++;
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("apache5 doExecute hook failed: %s", e);
            }
        }
        if (hooked == 0) {
            throw new RuntimeException("apache5-no-doExecute");
        }
        NetTap.getXposedLogger().log(
                "installed hook: %s.doExecute (%d overloads)", CLIENT, hooked);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_APACHE5);
        } catch (Throwable ignored) {
        }
    }

    private static final class DoExecuteHook extends XC_MethodHook {
        private final String packageName;

        DoExecuteHook(String packageName) {
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
                Object target = findArgByName(param.args, "org.apache.hc.core5.http.HttpHost");
                Object request = findArgByName(param.args,
                        "org.apache.hc.core5.http.ClassicHttpRequest");
                if (request == null) {
                    request = findArgByName(param.args, "org.apache.hc.core5.http.HttpRequest");
                }

                String url = requestUrl(target, request);
                String method = stringOf(safeCall(request, "getMethod"));
                LinkedHashMap<String, String> reqHeaders = collectApacheHeaders(request);
                CaptureBody reqBody = bodyFromEntity(safeCall(request, "getEntity"));

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
                            Object c = XposedHelpers.callMethod(response, "getCode");
                            if (c instanceof Integer) {
                                responseCode = (Integer) c;
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            Object msg = XposedHelpers.callMethod(response, "getReasonPhrase");
                            if (msg != null) {
                                responseMessage = msg.toString();
                            }
                        } catch (Throwable ignored) {
                        }
                        resHeaders = collectApacheHeaders(response);
                        resBody = bodyFromEntity(safeCall(response, "getEntity"));
                    }
                }

                CaptureEvent event = CaptureEvent.complete(
                        CaptureEvent.nextId(ID_PREFIX),
                        CaptureEvent.timestampNow(),
                        packageName,
                        HOOK_NAME,
                        method == null ? "" : method,
                        url == null ? "" : url,
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
                    MetricsReporter.incCaptured(packageName, MetricsReporter.LAYER_APACHE5);
                } catch (Throwable ignored) {
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("apache5 after failed: %s", e);
            }
        }
    }

    private static Object findArgByName(Object[] args, String typeName) {
        if (args == null) {
            return null;
        }
        for (Object a : args) {
            if (a == null) {
                continue;
            }
            Class<?> c = a.getClass();
            while (c != null) {
                if (typeName.equals(c.getName())) {
                    return a;
                }
                for (Class<?> i : c.getInterfaces()) {
                    if (typeName.equals(i.getName())) {
                        return a;
                    }
                }
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static String requestUrl(Object host, Object request) {
        try {
            Object uri = XposedHelpers.callMethod(request, "getUri");
            if (uri != null) {
                return uri.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object path = XposedHelpers.callMethod(request, "getRequestUri");
            String hostStr = host == null ? "" : host.toString();
            return hostStr + (path == null ? "" : path.toString());
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static LinkedHashMap<String, String> collectApacheHeaders(Object messageSupport) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (messageSupport == null) {
            return out;
        }
        try {
            Object arr = XposedHelpers.callMethod(messageSupport, "getHeaders");
            if (arr instanceof Object[]) {
                for (Object h : (Object[]) arr) {
                    if (h == null) {
                        continue;
                    }
                    String name = stringOf(safeCall(h, "getName"));
                    String value = stringOf(safeCall(h, "getValue"));
                    if (name != null) {
                        String prev = out.get(name);
                        out.put(name, prev == null ? (value == null ? "" : value)
                                : prev + "," + (value == null ? "" : value));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static CaptureBody bodyFromEntity(Object entity) {
        if (entity == null) {
            return CaptureBody.omitted(null, -1L, null, "no body");
        }
        boolean repeatable = false;
        try {
            Object r = XposedHelpers.callMethod(entity, "isRepeatable");
            repeatable = r instanceof Boolean && (Boolean) r;
        } catch (Throwable ignored) {
        }
        String contentType = null;
        try {
            contentType = stringOf(XposedHelpers.callMethod(entity, "getContentType"));
        } catch (Throwable ignored) {
        }
        long contentLength = -1L;
        try {
            Object cl = XposedHelpers.callMethod(entity, "getContentLength");
            if (cl instanceof Number) {
                contentLength = ((Number) cl).longValue();
            }
        } catch (Throwable ignored) {
        }
        if (!repeatable) {
            return CaptureBody.omitted(contentType, contentLength, null,
                    "apache5 entity not repeatable");
        }
        try {
            Object stream = XposedHelpers.callMethod(entity, "getContent");
            if (!(stream instanceof java.io.InputStream)) {
                return CaptureBody.omitted(contentType, contentLength, null,
                        "apache5 entity has no InputStream");
            }
            java.io.InputStream in = (java.io.InputStream) stream;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int max = CaptureConfig.MAX_BODY_BYTES;
            boolean truncated = false;
            long total = 0L;
            while (buf.size() < max) {
                int n = in.read(tmp);
                if (n < 0) {
                    break;
                }
                total += n;
                int remaining = max - buf.size();
                int copy = Math.min(n, remaining);
                buf.write(tmp, 0, copy);
                if (copy < n) {
                    truncated = true;
                    // Caller still has the full stream (isRepeatable() == true).
                    // Stop reading instead of draining a potentially huge payload.
                    break;
                }
            }
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            return CaptureBody.fromBytes(buf.toByteArray(),
                    truncated ? -1L : total, truncated, "empty");
        } catch (Throwable e) {
            return CaptureBody.omitted(contentType, contentLength, null,
                    "apache5 entity read failed");
        }
    }

    private static Object safeCall(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(target, method);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String stringOf(Object o) {
        return o == null ? null : o.toString();
    }
}
