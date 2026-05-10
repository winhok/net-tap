package xyz.winhok.nettap;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final String ID_PREFIX = "cronet-bidi";
    private static final CronetInstallRegistry INSTALL_REGISTRY = new CronetInstallRegistry();
    private static final RequestLifecycle<Object, BidiState> LIFECYCLE =
            new RequestLifecycle<>(() -> {
                throw new IllegalStateException("cronet-bidi requires explicit BidiState");
            });

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
        return INSTALL_REGISTRY.installOnce(
                classLoader,
                candidates,
                MetricsReporter.LAYER_CRONET_BIDI,
                packageName,
                (cls, collector) -> {
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllConstructors(
                            cls, new CtorHook(packageName)));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onResponseHeadersReceived", new HeadersHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onReadCompleted", new ReadHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onWriteCompleted", new WriteHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onSucceeded", new SucceededHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onFailed", new FailedHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onCanceled", new CanceledHook()));
                });
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
                LIFECYCLE.start(
                        param.thisObject,
                        new BidiState(packageName, url, System.nanoTime()));
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class HeadersHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            BidiState state = LIFECYCLE.get(param.thisObject);
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
        BidiState state = LIFECYCLE.get(param.thisObject);
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
        if (stream == null) {
            return;
        }
        LIFECYCLE.finishOnce(stream, state -> {
            CaptureEvent event = CaptureEvent.fromParsed(
                    CaptureEvent.nextId(ID_PREFIX),
                    CaptureEvent.timestampNow(),
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
                    CaptureEvent.elapsedMsSince(state.startedNanos),
                    error
            );
            state.requestBody = null;
            state.responseBody = null;
            CaptureRecorder.record(event);
            try {
                MetricsReporter.incCaptured(state.packageName, MetricsReporter.LAYER_CRONET_BIDI);
            } catch (Throwable ignored) {
            }
        });
    }

    private static CaptureBody bodyFrom(CronetBodyAccumulator acc, String emptyReason) {
        if (acc == null) {
            return CaptureBody.omitted(null, -1L, null, emptyReason);
        }
        return CaptureBody.fromBytes(
                acc.getBytes(),
                acc.getTotalBytesObserved(),
                acc.isTruncated(),
                emptyReason);
    }

    private static LinkedHashMap<String, String> extractHeadersFromInfo(Object info) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (info == null) {
            return out;
        }
        try {
            Object raw = Reflect.invokeNoArg(info, "getAllHeaders");
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
            Object code = Reflect.invokeNoArg(info, "getHttpStatusCode");
            if (code instanceof Number) {
                return ((Number) code).intValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static final class BidiState {
        final String packageName;
        final long startedNanos;
        String url;
        int responseCode;
        LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
        CronetBodyAccumulator requestBody;
        CronetBodyAccumulator responseBody;

        BidiState(String packageName, String url, long startedNanos) {
            this.packageName = packageName;
            this.url = url;
            this.startedNanos = startedNanos;
        }
    }
}
