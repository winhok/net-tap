package xyz.winhok.nettap;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Hook installer for {@code org.chromium.net.impl.CronetBidirectionalStream}
 * (stock and TTNet-shaded). Captures request+response bodies by accumulating
 * {@code onReadCompleted} / {@code onWriteCompleted} ByteBuffer slices. Used
 * for gRPC-over-Cronet and HTTP/2 streaming when the app bypasses the stock
 * CronetUrlRequest path.
 */
public final class CronetBidirectionalStreamHook {

    private static final String HOOK_NAME = "CronetBidirectionalStream";
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final CronetInstallRegistry INSTALL_REGISTRY = new CronetInstallRegistry();
    private static final Map<Object, BidiState> STATES = Collections.synchronizedMap(
            new WeakHashMap<Object, BidiState>());

    private CronetBidirectionalStreamHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        List<Class<?>> candidates = CronetCandidates.resolveAllWithSuffix(
                classLoader, CronetCandidates.CRONET_BIDIRECTIONAL_STREAM_SUFFIX);
        if (candidates.isEmpty()) {
            return false;
        }

        int hooked = 0;
        for (Class<?> cls : candidates) {
            String className = cls.getName();
            synchronized (INSTALL_REGISTRY) {
                if (INSTALL_REGISTRY.isInstalled(classLoader, className)) {
                    hooked++;
                    continue;
                }
                List<XC_MethodHook.Unhook> installedHooks = new ArrayList<>();
                try {
                    addAll(installedHooks, XposedBridge.hookAllConstructors(
                            cls, new CtorHook(packageName)));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onResponseHeadersReceived", new HeadersHook()));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onReadCompleted", new ReadHook()));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onWriteCompleted", new WriteHook()));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onSucceeded", new SucceededHook()));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onFailed", new FailedHook()));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onCanceled", new CanceledHook()));
                    INSTALL_REGISTRY.markInstalled(classLoader, className);
                    NetTap.getXposedLogger().log(
                            "installed hook: %s", className);
                    try {
                        MetricsReporter.incInstalled(
                                packageName, MetricsReporter.LAYER_CRONET_BIDI);
                    } catch (Throwable ignored) {
                    }
                    hooked++;
                } catch (Throwable e) {
                    for (int i = installedHooks.size() - 1; i >= 0; i--) {
                        try {
                            installedHooks.get(i).unhook();
                        } catch (Throwable ignored) {
                        }
                    }
                    INSTALL_REGISTRY.unmarkInstalled(classLoader, className);
                    NetTap.getXposedLogger().log(
                            "failed to install %s hook: %s", className, e);
                }
            }
        }
        return hooked > 0;
    }

    private static void addAll(List<XC_MethodHook.Unhook> dst, java.util.Set<XC_MethodHook.Unhook> src) {
        if (src != null) {
            dst.addAll(src);
        }
    }

    private static final class CtorHook extends XC_MethodHook {
        private final String packageName;

        CtorHook(String packageName) {
            this.packageName = packageName;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                String url = null;
                if (param.args != null) {
                    for (Object a : param.args) {
                        if (a instanceof String) {
                            url = (String) a;
                            break;
                        }
                    }
                }
                STATES.put(
                        param.thisObject,
                        new BidiState(packageName, url, System.nanoTime()));
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class HeadersHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            BidiState state = STATES.get(param.thisObject);
            if (state == null || param.args == null || param.args.length < 1) {
                return;
            }
            synchronized (state) {
                state.responseHeaders.putAll(extractHeadersFromInfo(param.args[0]));
                state.responseCode = extractCode(param.args[0], state.responseCode);
            }
        }
    }

    private static final class ReadHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            appendChunk(param, false);
        }
    }

    private static final class WriteHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            appendChunk(param, true);
        }
    }

    private static void appendChunk(XC_MethodHook.MethodHookParam param, boolean isWrite) {
        BidiState state = STATES.get(param.thisObject);
        if (state == null || param.args == null) {
            return;
        }
        ByteBuffer bb = null;
        for (Object a : param.args) {
            if (a instanceof ByteBuffer) {
                bb = (ByteBuffer) a;
                break;
            }
        }
        if (bb == null) {
            return;
        }
        int initialPosition = 0;
        int initialLimit = bb.limit();
        int bytesReady = bb.position();
        synchronized (state) {
            CronetBodyAccumulator acc = isWrite
                    ? state.requestBody
                    : state.responseBody;
            if (acc == null) {
                acc = new CronetBodyAccumulator();
                if (isWrite) {
                    state.requestBody = acc;
                } else {
                    state.responseBody = acc;
                }
            }
            acc.appendFromBuffer(
                    bb, bytesReady, initialPosition, initialLimit,
                    CaptureConfig.MAX_BODY_BYTES);
        }
    }

    private static final class SucceededHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            recordOnce(param.thisObject, HOOK_NAME + ".onSucceeded", null);
        }
    }

    private static final class FailedHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            String err = "cronet-bidi failed";
            if (param.args != null && param.args.length > 1 && param.args[1] != null) {
                err = String.valueOf(param.args[1]);
            }
            recordOnce(param.thisObject, HOOK_NAME + ".onFailed", err);
        }
    }

    private static final class CanceledHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            recordOnce(param.thisObject, HOOK_NAME + ".onCanceled", "canceled");
        }
    }

    private static void recordOnce(Object stream, String hookName, String error) {
        BidiState state = STATES.get(stream);
        if (state == null) {
            return;
        }
        CaptureEvent event;
        synchronized (state) {
            if (state.recorded) {
                return;
            }
            state.recorded = true;
            event = CaptureEvent.complete(
                    "cronet-bidi-" + NEXT_ID.incrementAndGet(),
                    timestamp(),
                    state.packageName,
                    hookName,
                    null,
                    state.url,
                    new LinkedHashMap<String, String>(),
                    bodyFrom(state.requestBody, "cronet bidi request body not observed"),
                    state.responseCode,
                    null,
                    state.responseHeaders,
                    bodyFrom(state.responseBody, "cronet bidi response body not observed"),
                    Math.max(0L, (System.nanoTime() - state.startedNanos) / 1_000_000L),
                    error
            );
        }
        CaptureRecorder.record(event);
        try {
            MetricsReporter.incCaptured(state.packageName, MetricsReporter.LAYER_CRONET_BIDI);
        } catch (Throwable ignored) {
        }
    }

    private static CaptureBody bodyFrom(CronetBodyAccumulator acc, String emptyReason) {
        if (acc == null) {
            return CaptureBody.omitted(null, -1L, null, emptyReason);
        }
        byte[] bytes = acc.getBytes();
        if (bytes == null || bytes.length == 0) {
            return CaptureBody.omitted(null, acc.getTotalBytesObserved(), null, emptyReason);
        }
        try {
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return CaptureBody.text(
                    null, acc.getTotalBytesObserved(), null, acc.isTruncated(), text);
        } catch (Throwable t) {
            return CaptureBody.omitted(
                    null, acc.getTotalBytesObserved(), null, "decode failed");
        }
    }

    private static LinkedHashMap<String, String> extractHeadersFromInfo(Object info) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (info == null) {
            return out;
        }
        try {
            java.lang.reflect.Method m = info.getClass().getMethod("getAllHeaders");
            Object raw = m.invoke(info);
            if (raw instanceof Map) {
                for (Object e : ((Map<?, ?>) raw).entrySet()) {
                    Map.Entry<?, ?> me = (Map.Entry<?, ?>) e;
                    Object k = me.getKey();
                    Object v = me.getValue();
                    if (k == null) {
                        continue;
                    }
                    out.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static int extractCode(Object info, int fallback) {
        if (info == null) {
            return fallback;
        }
        try {
            Object code = info.getClass().getMethod("getHttpStatusCode").invoke(info);
            if (code instanceof Number) {
                return ((Number) code).intValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static String timestamp() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(System.currentTimeMillis()));
    }

    private static final class BidiState {
        final String packageName;
        final long startedNanos;
        String url;
        int responseCode;
        LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
        CronetBodyAccumulator requestBody;
        CronetBodyAccumulator responseBody;
        boolean recorded;

        BidiState(String packageName, String url, long startedNanos) {
            this.packageName = packageName;
            this.url = url;
            this.startedNanos = startedNanos;
        }
    }
}
