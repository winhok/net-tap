package xyz.winhok.nettap;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class CronetUrlRequestHook {
    private static final String HOOK_NAME = "CronetUrlRequest";
    private static final String ID_PREFIX = "cronet-url-request";
    private static final CronetInstallRegistry INSTALL_REGISTRY = new CronetInstallRegistry();
    private static final RequestLifecycle<Object, HookState> LIFECYCLE =
            new RequestLifecycle<>(() -> {
                throw new IllegalStateException("cronet-url requires explicit HookState");
            });

    private CronetUrlRequestHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        List<Class<?>> candidates = CronetCandidates.resolveAll(classLoader);
        if (candidates.isEmpty()) {
            NetTap.getXposedLogger().log(
                    "hook unavailable: %s",
                    CronetCandidates.CRONET_URL_REQUEST_SUFFIX
            );
            return false;
        }
        return INSTALL_REGISTRY.installOnce(
                classLoader,
                candidates,
                MetricsReporter.LAYER_CRONET,
                packageName,
                (cls, collector) -> {
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllConstructors(
                            cls, new ConstructorHook(packageName)));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onResponseStarted", new ResponseStartedHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onRedirectReceived", new RedirectHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onSucceeded", new SucceededHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onError", new ErrorHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onCanceled", new CanceledHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onReadCompleted", new ReadCompletedHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "setHttpMethod", new SetHttpMethodHook()));
                });
    }

    private static final class ConstructorHook extends XC_MethodHook {
        private final String packageName;

        private ConstructorHook(String packageName) {
            this.packageName = packageName;
        }

        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            String url = firstString(param.args);
            HookState state = new HookState(packageName, url, System.nanoTime());
            LIFECYCLE.start(param.thisObject, state);
            NetTap.getXposedLogger().log("%s created: %s", HOOK_NAME, url != null ? url : "(unknown url)");
        }
    }

    private static final class ResponseStartedHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            HookState state = state(param.thisObject);
            if (state == null) {
                return;
            }

            synchronized (state) {
                state.responseCode = intArg(param.args, 0, 0);
                state.responseMessage = stringArg(param.args, 1);
                state.responseHeaders = CronetHeaders.fromArray(stringArrayArg(param.args, 2));
                putIfPresent(state.responseHeaders, "cronet-negotiated-protocol", stringArg(param.args, 4));
                putIfPresent(state.responseHeaders, "cronet-proxy-server", stringArg(param.args, 5));
            }

            NetTap.getXposedLogger().log(
                    "%s response started: %s %d",
                    HOOK_NAME,
                    state.url != null ? state.url : "(unknown url)",
                    state.responseCode
            );
        }
    }

    private static final class RedirectHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            HookState state = state(param.thisObject);
            if (state == null) {
                return;
            }

            String redirectUrl = stringArg(param.args, 0);
            synchronized (state) {
                if (redirectUrl != null) {
                    state.url = redirectUrl;
                }
                state.responseCode = intArg(param.args, 1, state.responseCode);
                state.responseMessage = stringArg(param.args, 2);
                state.responseHeaders = CronetHeaders.fromArray(stringArrayArg(param.args, 3));
                putIfPresent(state.responseHeaders, "cronet-negotiated-protocol", stringArg(param.args, 5));
                putIfPresent(state.responseHeaders, "cronet-proxy-server", stringArg(param.args, 6));
            }

            NetTap.getXposedLogger().log(
                    "%s redirect: %s %d",
                    HOOK_NAME,
                    state.url != null ? state.url : "(unknown url)",
                    state.responseCode
            );
        }
    }

    private static final class SucceededHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            recordOnce(param.thisObject, "CronetUrlRequest.onSucceeded", null);
        }
    }

    private static final class ErrorHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            String error = "Cronet error="
                    + intArg(param.args, 0, 0)
                    + ", internalError="
                    + intArg(param.args, 1, 0)
                    + ", quicError="
                    + intArg(param.args, 2, 0)
                    + ", message="
                    + stringArg(param.args, 3);
            recordOnce(param.thisObject, "CronetUrlRequest.onError", error);
        }
    }

    private static final class CanceledHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            recordOnce(param.thisObject, "CronetUrlRequest.onCanceled", "Cronet request canceled");
        }
    }

    /**
     * Cronet feeds response bytes one chunk at a time through
     * {@code onReadCompleted(ByteBuffer, int bytesRead, int initialPosition,
     * int initialLimit, long receivedByteCount)}. We accumulate the slices
     * directly because the buffer's {@code position} has already been
     * advanced past {@code initialPosition + bytesRead}, so we cannot rely
     * on the buffer's mutable state to find the freshly written bytes.
     */
    private static final class ReadCompletedHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                HookState state = state(param.thisObject);
                if (state == null) {
                    return;
                }
                Object[] args = param.args;
                if (args == null || args.length < 4) {
                    return;
                }
                if (!(args[0] instanceof ByteBuffer)) {
                    return;
                }
                ByteBuffer buffer = (ByteBuffer) args[0];
                int bytesRead = intArg(args, 1, 0);
                int initialPosition = intArg(args, 2, 0);
                int initialLimit = intArg(args, 3, 0);

                synchronized (state) {
                    if (state.responseBodyAccumulator == null) {
                        state.responseBodyAccumulator = new CronetBodyAccumulator();
                    }
                    state.responseBodyAccumulator.appendFromBuffer(
                            buffer,
                            bytesRead,
                            initialPosition,
                            initialLimit,
                            CaptureConfig.MAX_BODY_BYTES
                    );
                }
            } catch (Throwable e) {
                NetTap.getXposedLogger().log(
                        "failed to capture Cronet response body chunk: %s", e);
            }
        }
    }

    private static final class SetHttpMethodHook extends XC_MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            HookState state = state(param.thisObject);
            if (state == null) {
                return;
            }
            String method = stringArg(param.args, 0);
            if (method == null) {
                return;
            }
            synchronized (state) {
                state.method = method;
            }
        }
    }

    private static void recordOnce(Object request, String hookName, String error) {
        if (request == null) {
            return;
        }
        LIFECYCLE.finishOnce(request, state -> {
            CaptureEvent event = CaptureEvent.fromParsed(
                    CaptureEvent.nextId(ID_PREFIX),
                    CaptureEvent.timestampNow(),
                    state.packageName,
                    hookName,
                    state.method,
                    state.url,
                    new LinkedHashMap<>(),
                    buildRequestBody(state),
                    state.responseCode,
                    state.responseMessage,
                    state.responseHeaders,
                    buildResponseBody(state),
                    CaptureEvent.elapsedMsSince(state.startedNanos),
                    error
            );
            state.requestBodyAccumulator = null;
            state.responseBodyAccumulator = null;
            CaptureRecorder.record(event);
        });
    }

    static void attachRequestBodyChunk(
            Object request,
            ByteBuffer source,
            int bytesRead,
            int initialPosition,
            int initialLimit
    ) {
        if (request == null || source == null || bytesRead <= 0) {
            return;
        }
        HookState state = state(request);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.requestBodyAccumulator == null) {
                state.requestBodyAccumulator = new CronetBodyAccumulator();
            }
            state.requestBodyAccumulator.appendFromBuffer(
                    source, bytesRead, initialPosition, initialLimit,
                    CaptureConfig.MAX_BODY_BYTES);
        }
    }

    private static CaptureBody buildRequestBody(HookState state) {
        CronetBodyAccumulator accumulator = state.requestBodyAccumulator;
        if (accumulator == null) {
            return CaptureBody.omitted(
                    null, -1L, null,
                    "Cronet request body not observed");
        }
        return CaptureBody.fromBytes(
                accumulator.getBytes(),
                accumulator.getTotalBytesObserved(),
                accumulator.isTruncated(),
                "Cronet request body empty");
    }

    private static CaptureBody buildResponseBody(HookState state) {
        CronetBodyAccumulator accumulator = state.responseBodyAccumulator;
        if (accumulator == null) {
            return CaptureBody.omitted(
                    null, -1L, null,
                    "Cronet response body not observed"
            );
        }
        String contentType = CronetHeaders.valueIgnoreCase(state.responseHeaders, "content-type");
        String encoding = CronetHeaders.valueIgnoreCase(state.responseHeaders, "content-encoding");
        return CaptureBody.fromCronetResponseBytes(
                accumulator.getBytes(),
                contentType,
                accumulator.getTotalBytesObserved(),
                encoding,
                accumulator.isTruncated()
        );
    }

    private static HookState state(Object request) {
        if (request == null) {
            return null;
        }
        return LIFECYCLE.get(request);
    }

    private static String firstString(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String) {
                return (String) arg;
            }
        }
        return null;
    }

    private static int intArg(Object[] args, int index, int fallback) {
        if (args == null || index < 0 || index >= args.length || !(args[index] instanceof Number)) {
            return fallback;
        }
        return ((Number) args[index]).intValue();
    }

    private static String stringArg(Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length || !(args[index] instanceof String)) {
            return null;
        }
        return (String) args[index];
    }

    private static String[] stringArrayArg(Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length || !(args[index] instanceof String[])) {
            return null;
        }
        return (String[]) args[index];
    }

    private static void putIfPresent(LinkedHashMap<String, String> headers, String name, String value) {
        if (headers == null || value == null || value.isEmpty()) {
            return;
        }
        headers.put(name, value);
    }

    private static final class HookState {
        private final String packageName;
        private final long startedNanos;
        private String url;
        private String method = "GET";
        private int responseCode;
        private String responseMessage;
        private LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
        private CronetBodyAccumulator responseBodyAccumulator;
        private CronetBodyAccumulator requestBodyAccumulator;

        private HookState(String packageName, String url, long startedNanos) {
            this.packageName = packageName;
            this.url = url;
            this.startedNanos = startedNanos;
        }
    }
}
