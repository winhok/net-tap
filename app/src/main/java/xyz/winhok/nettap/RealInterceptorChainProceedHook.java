package xyz.winhok.nettap;

import java.util.LinkedHashMap;

import de.robv.android.xposed.XC_MethodHook;

/* okhttp3.internal.http.RealInterceptorChain.proceed(okhttp3.Request) hook. */
public class RealInterceptorChainProceedHook extends XC_MethodHook {
    private static final String HOOK_NAME = "RealInterceptorChain.proceed";
    private static final String ID_PREFIX = "real-interceptor-chain";
    private static final String STATE_EXTRA = "nettap.real_interceptor_chain_state";

    private final String packageName;

    public RealInterceptorChainProceedHook(String packageName) {
        this.packageName = packageName;
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        try {
            Object request = null;
            if (param.args != null && param.args.length > 0) {
                request = param.args[0];
            }
            param.setObjectExtra(STATE_EXTRA, new HookState(System.nanoTime(), request));
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("RealInterceptorChain.proceed before hook failed: %s", e);
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
                    HOOK_NAME,
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
            NetTap.getXposedLogger().logSafe("RealInterceptorChain.proceed after hook failed: %s", e);
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
