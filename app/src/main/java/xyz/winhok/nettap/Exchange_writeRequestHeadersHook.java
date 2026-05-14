package xyz.winhok.nettap;

import java.util.LinkedHashMap;

import de.robv.android.xposed.XC_MethodHook;

/* okhttp3.internal.connection.Exchange.writeRequestHeaders(okhttp3.Request r) hook. */
public class Exchange_writeRequestHeadersHook extends XC_MethodHook {
    private static final String HOOK_NAME = "Exchange.writeRequestHeaders";
    private static final String ID_PREFIX = "exchange-write-request-headers";

    private final String packageName;

    public Exchange_writeRequestHeadersHook(String packageName) {
        this.packageName = packageName;
    }

    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        try {
            Object request = null;
            if (param.args != null && param.args.length > 0) {
                request = param.args[0];
            }

            CaptureEvent event = CaptureEvent.fromParsed(
                    CaptureEvent.nextId(ID_PREFIX),
                    CaptureEvent.timestampNow(),
                    packageName,
                    HOOK_NAME,
                    ReflectiveOkHttp.method(request),
                    ReflectiveOkHttp.url(request),
                    ReflectiveOkHttp.headers(request),
                    ReflectiveOkHttp.requestBody(request),
                    0,
                    "",
                    new LinkedHashMap<>(),
                    CaptureBody.omitted(
                            null,
                            -1L,
                            null,
                            "response unavailable from request-only fallback"
                    ),
                    0L,
                    null
            );
            CaptureRecorder.record(event);
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("Exchange.writeRequestHeaders hook failed: %s", e);
        }
    }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        /* After hooked method. */
    }
}
