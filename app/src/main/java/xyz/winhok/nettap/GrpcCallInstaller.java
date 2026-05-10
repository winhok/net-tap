package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import android.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Installs hooks on {@code io.grpc.internal.ClientCallImpl} to capture
 * gRPC-over-HTTP/2 traffic. Needed for apps that route business logic through
 * grpc-over-okhttp, which bypasses OkHttp's {@code RealCall} entirely.
 *
 * <p>Hook points:
 * <ul>
 *   <li>{@code ClientCallImpl.start(Listener, Metadata)} — capture method
 *       name, request headers, swap the listener with a {@link GrpcListenerProxy}
 *       so responses can be observed.</li>
 *   <li>{@code ClientCallImpl.sendMessage(Object)} — capture first proto
 *       message as the "request body" equivalent.</li>
 * </ul>
 *
 * <p>Graceful degradation: if {@code io.grpc.internal.ClientCallImpl} is
 * absent the installer logs and returns false.
 */
public final class GrpcCallInstaller {

    private static final String CLIENT_CALL_IMPL = "io.grpc.internal.ClientCallImpl";
    private static final String CLIENT_CALL_LISTENER = "io.grpc.ClientCall$Listener";
    private static final String HOOK_NAME = "GrpcClientCall";

    private static final Map<Object, GrpcCaptureState> STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, GrpcCaptureState>());

    private GrpcCallInstaller() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("grpc", classLoader, () ->
                    installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("grpc install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        Class<?> impl = XposedHelpers.findClassIfExists(CLIENT_CALL_IMPL, classLoader);
        if (impl == null) {
            NetTap.getXposedLogger().log(
                    "gRPC not present in %s (missing %s)", packageName, CLIENT_CALL_IMPL);
            throw new RuntimeException("grpc-absent");
        }
        Class<?> listenerCls = XposedHelpers.findClassIfExists(CLIENT_CALL_LISTENER, classLoader);
        if (listenerCls == null) {
            NetTap.getXposedLogger().log(
                    "gRPC Listener class missing in %s", packageName);
            throw new RuntimeException("grpc-listener-absent");
        }

        int hookedMethods = 0;
        for (Method m : impl.getDeclaredMethods()) {
            if ("start".equals(m.getName()) && m.getParameterTypes().length == 2) {
                try {
                    XposedBridge.hookMethod(m, new StartHook(packageName, listenerCls));
                    hookedMethods++;
                } catch (Throwable e) {
                    NetTap.getXposedLogger().log("grpc start hook failed: %s", e);
                }
            } else if ("sendMessage".equals(m.getName()) && m.getParameterTypes().length == 1) {
                try {
                    XposedBridge.hookMethod(m, new SendMessageHook());
                    hookedMethods++;
                } catch (Throwable e) {
                    NetTap.getXposedLogger().log("grpc sendMessage hook failed: %s", e);
                }
            }
        }
        if (hookedMethods == 0) {
            throw new RuntimeException("grpc-no-methods");
        }
        NetTap.getXposedLogger().log(
                "installed hook: %s (%d methods) in %s",
                CLIENT_CALL_IMPL, hookedMethods, packageName);
        try {
            MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_GRPC);
        } catch (Throwable ignored) {
        }
    }

    static void recordFinal(GrpcCaptureState state) {
        if (state == null) {
            return;
        }
        CaptureEvent event;
        synchronized (state.mutex) {
            if (state.recorded) {
                return;
            }
            state.recorded = true;
            String url = "grpc://" + (state.authority == null ? "" : state.authority)
                    + (state.fullMethodName == null ? "" : state.fullMethodName);
            CaptureBody reqBody = buildBody(
                    state.requestBodyFirstMessage,
                    state.requestBodyObserved,
                    state.requestBodyTruncated);
            CaptureBody resBody = buildBody(
                    state.responseBodyFirstMessage,
                    state.responseBodyObserved,
                    state.responseBodyTruncated);
            long durationMs = Math.max(0L, (System.nanoTime() - state.startedNanos) / 1_000_000L);
            int responseCode = mapGrpcStatusToHttp(state.statusCode);
            event = CaptureEvent.complete(
                    state.id,
                    timestamp(),
                    state.packageName,
                    HOOK_NAME,
                    "POST",
                    url,
                    new LinkedHashMap<>(state.requestHeaders),
                    reqBody,
                    responseCode,
                    state.statusMessage == null ? "" : state.statusMessage,
                    new LinkedHashMap<>(state.responseHeaders),
                    resBody,
                    durationMs,
                    null
            );
        }
        CaptureRecorder.record(event);
        try {
            MetricsReporter.incCaptured(state.packageName, MetricsReporter.LAYER_GRPC);
        } catch (Throwable ignored) {
        }
    }

    private static CaptureBody buildBody(byte[] bytes, int observed, boolean truncated) {
        if (bytes == null || bytes.length == 0) {
            return CaptureBody.omitted(
                    "application/grpc", observed, null,
                    "grpc message not captured");
        }
        String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String text = "[grpc-proto base64:" + b64 + "]";
        return CaptureBody.text(
                "application/grpc", observed, null, truncated, text);
    }

    private static int mapGrpcStatusToHttp(int code) {
        switch (code) {
            case 0: return 200;
            case 3: return 400;
            case 16: return 401;
            case 7: return 403;
            case 5: return 404;
            case 4: return 408;
            case 14: return 503;
            default: return code == 0 ? 200 : 500;
        }
    }

    private static String timestamp() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(System.currentTimeMillis()));
    }

    private static final class StartHook extends XC_MethodHook {
        private final String packageName;
        private final Class<?> listenerClass;

        StartHook(String packageName, Class<?> listenerClass) {
            this.packageName = packageName;
            this.listenerClass = listenerClass;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                GrpcCaptureState state = new GrpcCaptureState(packageName);
                extractCallMetadata(param.thisObject, state);
                if (param.args != null && param.args.length >= 2) {
                    state.requestHeaders.putAll(GrpcMetadata.extractHeaders(param.args[1]));
                    if (param.args[0] != null) {
                        Object wrapped = GrpcListenerProxy.wrap(
                                param.args[0], listenerClass, state);
                        param.args[0] = wrapped;
                    }
                }
                STATES.put(param.thisObject, state);
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("grpc start before failed: %s", e);
            }
        }

        private static void extractCallMetadata(Object impl, GrpcCaptureState state) {
            if (impl == null) {
                return;
            }
            Class<?> cls = impl.getClass();
            while (cls != null) {
                for (Field f : cls.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    String tn = t == null ? "" : t.getName();
                    if ("io.grpc.MethodDescriptor".equals(tn)) {
                        extractMethodDescriptor(impl, f, state);
                    } else if ("java.lang.String".equals(tn) && "authority".equalsIgnoreCase(f.getName())) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(impl);
                            if (v instanceof String) {
                                state.authority = (String) v;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        }

        private static void extractMethodDescriptor(Object impl, Field f, GrpcCaptureState state) {
            try {
                f.setAccessible(true);
                Object md = f.get(impl);
                if (md == null) {
                    return;
                }
                Object name = md.getClass().getMethod("getFullMethodName").invoke(md);
                if (name instanceof String) {
                    state.fullMethodName = (String) name;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class SendMessageHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                GrpcCaptureState state = STATES.get(param.thisObject);
                if (state == null || param.args == null || param.args.length < 1) {
                    return;
                }
                Object message = param.args[0];
                if (message == null) {
                    return;
                }
                synchronized (state.mutex) {
                    if (state.requestBodyFirstMessage != null) {
                        return;
                    }
                }
                byte[] bytes = null;
                try {
                    Method m = message.getClass().getMethod("toByteArray");
                    Object r = m.invoke(message);
                    if (r instanceof byte[]) {
                        bytes = (byte[]) r;
                    }
                } catch (Throwable ignored) {
                }
                if (bytes == null) {
                    return;
                }
                int max = CaptureConfig.MAX_BODY_BYTES;
                boolean truncated = bytes.length > max;
                byte[] stored = truncated ? java.util.Arrays.copyOf(bytes, max) : bytes;
                synchronized (state.mutex) {
                    if (state.requestBodyFirstMessage == null) {
                        state.requestBodyFirstMessage = stored;
                        state.requestBodyObserved = bytes.length;
                        state.requestBodyTruncated = truncated;
                    }
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("grpc sendMessage before failed: %s", e);
            }
        }
    }
}
