package xyz.winhok.nettap;

import de.robv.android.xposed.XC_MethodHook;

/* okhttp3.internal.http.RealInterceptorChain.proceed(okhttp3.Request) hook. */
public class RealInterceptorChainProceedHook extends XC_MethodHook {
    private static final String HOOK_NAME = "RealInterceptorChain.proceed";
    private static final String ID_PREFIX = "real-interceptor-chain";
    private static final MethodScopeState<CallState> SCOPE =
            new MethodScopeState<>("nettap.real_interceptor_chain_state", CallState::new);

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
            SCOPE.start(new ParamScope(param)).request = request;
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("RealInterceptorChain.proceed before hook failed: %s", e);
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
                    HOOK_NAME,
                    request,
                    response,
                    snap.getDurationMs(),
                    throwable
            );
            CaptureRecorder.record(event);
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("RealInterceptorChain.proceed after hook failed: %s", e);
        }
    }

    private static final class CallState {
        Object request;
    }
}
