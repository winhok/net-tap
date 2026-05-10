package xyz.winhok.nettap;

import java.util.LinkedHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/* okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain$okhttp() hook. */
public class RealCallResponseHook extends XC_MethodHook {
    private static final String ID_PREFIX = "real-call-response";
    private static final String STATE_EXTRA = "nettap.real_call_response_state";

    private final String packageName;
    private final String hookName;

    public RealCallResponseHook(String packageName) {
        this(packageName, "RealCall.getResponseWithInterceptorChain$okhttp");
    }

    public RealCallResponseHook(String packageName, String hookName) {
        this.packageName = packageName;
        this.hookName = hookName;
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        try {
            Object request = requestFromCall(param.thisObject);
            String url = ReflectiveOkHttp.url(request);
            NetTap.getXposedLogger().log("%s triggered: %s", hookName, url != null ? url : "(unknown url)");
            param.setObjectExtra(STATE_EXTRA, new HookState(System.nanoTime(), request));
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe(hookName + " before hook failed: %s", e);
        }
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        try {
            HookState state = state(param);
            Object response = param.getResult();
            Throwable throwable = param.getThrowable();
            Object request = requestFor(response, state == null ? null : state.request);
            long durationMs = state == null ? 0L : CaptureEvent.elapsedMsSince(state.startedNanos);

            CaptureEvent event = CaptureEvent.complete(
                    CaptureEvent.nextId(ID_PREFIX),
                    CaptureEvent.timestampNow(),
                    packageName,
                    hookName,
                    ReflectiveOkHttp.method(request),
                    ReflectiveOkHttp.url(request),
                    ReflectiveOkHttp.headers(request),
                    ReflectiveOkHttp.requestBody(request),
                    response == null ? 0L : ReflectiveOkHttp.responseCode(response),
                    response == null ? "" : ReflectiveOkHttp.responseMessage(response),
                    response == null ? new LinkedHashMap<>() : ReflectiveOkHttp.headers(response),
                    response == null
                            ? CaptureBody.omitted(null, -1L, null, "response unavailable")
                            : ReflectiveOkHttp.responseBody(response),
                    durationMs,
                    throwable == null ? null : String.valueOf(throwable)
            );
            CaptureRecorder.record(event);
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe(hookName + " after hook failed: %s", e);
        }
    }

    private static Object requestFromCall(Object call) {
        Object request = fieldValue(call, "originalRequest");
        if (request != null) {
            return request;
        }
        request = methodValue(call, "originalRequest");
        if (request != null) {
            return request;
        }
        request = fieldValue(call, "request");
        if (request != null) {
            return request;
        }
        return methodValue(call, "request");
    }

    private static Object fieldValue(Object target, String name) {
        try {
            return XposedHelpers.getObjectField(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object methodValue(Object target, String name) {
        try {
            return XposedHelpers.callMethod(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static HookState state(MethodHookParam param) {
        try {
            Object value = param.getObjectExtra(STATE_EXTRA);
            if (value instanceof HookState) {
                return (HookState) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object requestFor(Object response, Object fallback) {
        Object responseRequest = ReflectiveOkHttp.requestFromResponse(response);
        if (responseRequest != null) {
            return responseRequest;
        }
        return fallback;
    }

    private static final class HookState {
        private final long startedNanos;
        private final Object request;

        private HookState(long startedNanos, Object request) {
            this.startedNanos = startedNanos;
            this.request = request;
        }
    }
}
