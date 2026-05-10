package xyz.winhok.nettap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/* okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain$okhttp() hook. */
public class RealCallResponseHook extends XC_MethodHook {
    private static final String ID_PREFIX = "real-call-response";
    private static final MethodScopeState<CallState> SCOPE =
            new MethodScopeState<>("nettap.real_call_response_state", CallState::new);

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
            SCOPE.start(new ParamScope(param)).request = request;
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe(hookName + " before hook failed: %s", e);
        }
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        try {
            MethodScopeState.Snapshot<CallState> snap = SCOPE.end(new ParamScope(param));
            CallState state = snap.getState();
            Object response = param.getResult();
            Throwable throwable = param.getThrowable();
            Object request = ReflectiveOkHttp.requestForResponse(response, state == null ? null : state.request);

            CaptureEvent event = CaptureEvent.fromOkHttpCall(
                    CaptureEvent.nextId(ID_PREFIX),
                    CaptureEvent.timestampNow(),
                    packageName,
                    hookName,
                    request,
                    response,
                    snap.getDurationMs(),
                    throwable
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

    private static final class CallState {
        Object request;
    }
}
