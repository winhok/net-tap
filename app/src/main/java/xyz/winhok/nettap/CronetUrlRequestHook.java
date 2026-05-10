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
import java.util.Set;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class CronetUrlRequestHook {
    private static final String HOOK_NAME = "CronetUrlRequest";
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final CronetInstallRegistry INSTALL_REGISTRY = new CronetInstallRegistry();
    private static final Map<Object, HookState> STATES = Collections.synchronizedMap(
            new WeakHashMap<Object, HookState>()
    );

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

        int hooked = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Class<?> cronetUrlRequest = candidates.get(i);
            String className = cronetUrlRequest.getName();
            synchronized (INSTALL_REGISTRY) {
                if (INSTALL_REGISTRY.isInstalled(classLoader, className)) {
                    hooked++;
                    continue;
                }
                List<XC_MethodHook.Unhook> installedHooks = new ArrayList<>();
                try {
                    addUnhooks(installedHooks, XposedBridge.hookAllConstructors(
                            cronetUrlRequest, new ConstructorHook(packageName)));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "onResponseStarted", new ResponseStartedHook()));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "onRedirectReceived", new RedirectHook()));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "onSucceeded", new SucceededHook()));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "onError", new ErrorHook()));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "onCanceled", new CanceledHook()));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "onReadCompleted", new ReadCompletedHook()));
                    addUnhooks(installedHooks, XposedBridge.hookAllMethods(
                            cronetUrlRequest, "setHttpMethod", new SetHttpMethodHook()));
                    INSTALL_REGISTRY.markInstalled(classLoader, className);
                    NetTap.getXposedLogger().log("installed hook: %s", className);
                    hooked++;
                } catch (Throwable e) {
                    rollbackHooks(installedHooks, className);
                    INSTALL_REGISTRY.unmarkInstalled(classLoader, className);
                    NetTap.getXposedLogger().log(
                            "failed to install %s hook: %s", className, e);
                }
            }
        }
        return hooked > 0;
    }

    private static void addUnhooks(
            List<XC_MethodHook.Unhook> target,
            Set<XC_MethodHook.Unhook> unhooks
    ) {
        if (target != null && unhooks != null) {
            target.addAll(unhooks);
        }
    }

    private static void rollbackHooks(List<XC_MethodHook.Unhook> hooks, String className) {
        if (hooks == null) {
            return;
        }
        for (int i = hooks.size() - 1; i >= 0; i--) {
            XC_MethodHook.Unhook hook = hooks.get(i);
            if (hook == null) {
                continue;
            }
            try {
                hook.unhook();
            } catch (Throwable e) {
                NetTap.getXposedLogger().log(
                        "failed to rollback %s hook: %s", className, e);
            }
        }
    }

    private static final class ConstructorHook extends XC_MethodHook {
        private final String packageName;

        private ConstructorHook(String packageName) {
            this.packageName = packageName;
        }

        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            String url = firstString(param.args);
            HookState state = new HookState(packageName, url, System.nanoTime());
            STATES.put(param.thisObject, state);
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
        HookState state = state(request);
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
                    nextId(),
                    timestamp(),
                    state.packageName,
                    hookName,
                    state.method,
                    state.url,
                    new LinkedHashMap<String, String>(),
                    buildRequestBody(state),
                    state.responseCode,
                    state.responseMessage,
                    state.responseHeaders,
                    buildResponseBody(state),
                    durationMs(state.startedNanos),
                    error
            );
        }

        CaptureRecorder.record(event);
    }

    /**
     * Build the response-body {@link CaptureBody} for {@code state}, using
     * any bytes captured by the {@link ReadCompletedHook}. Caller must hold
     * {@code state}'s monitor; the method does no additional locking.
     */
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
        byte[] bytes = accumulator.getBytes();
        if (bytes == null || bytes.length == 0) {
            return CaptureBody.omitted(
                    null, accumulator.getTotalBytesObserved(), null,
                    "Cronet request body empty");
        }
        try {
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return CaptureBody.text(
                    null,
                    accumulator.getTotalBytesObserved(),
                    null,
                    accumulator.isTruncated(),
                    text);
        } catch (Throwable t) {
            return CaptureBody.omitted(
                    null, accumulator.getTotalBytesObserved(), null,
                    "Cronet request body decode failed");
        }
    }

    private static CaptureBody buildResponseBody(HookState state) {
        CronetBodyAccumulator accumulator = state.responseBodyAccumulator;
        if (accumulator == null) {
            return CaptureBody.omitted(
                    null, -1L, null,
                    "Cronet response body not observed"
            );
        }
        String contentType = headerValue(state.responseHeaders, "content-type");
        String encoding = headerValue(state.responseHeaders, "content-encoding");
        return CronetResponseBodyDecoder.decodeCronetResponseBody(
                accumulator.getBytes(),
                contentType,
                encoding,
                accumulator.getTotalBytesObserved(),
                accumulator.isTruncated()
        );
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static HookState state(Object request) {
        if (request == null) {
            return null;
        }
        return STATES.get(request);
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
        if (headers == null || value == null || value.length() == 0) {
            return;
        }
        headers.put(name, value);
    }

    private static long durationMs(long startedNanos) {
        long elapsedNanos = System.nanoTime() - startedNanos;
        if (elapsedNanos <= 0L) {
            return 0L;
        }
        return elapsedNanos / 1_000_000L;
    }

    private static String nextId() {
        return "cronet-url-request-" + NEXT_ID.incrementAndGet();
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(System.currentTimeMillis()));
    }

    private static final class HookState {
        private final String packageName;
        private final long startedNanos;
        private String url;
        private String method = "GET";
        private int responseCode;
        private String responseMessage;
        private LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
        private boolean recorded;
        private CronetBodyAccumulator responseBodyAccumulator;
        private CronetBodyAccumulator requestBodyAccumulator;

        private HookState(String packageName, String url, long startedNanos) {
            this.packageName = packageName;
            this.url = url;
            this.startedNanos = startedNanos;
        }
    }
}
