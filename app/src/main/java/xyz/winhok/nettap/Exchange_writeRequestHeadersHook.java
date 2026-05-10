package xyz.winhok.nettap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;


// okhttp3.internal.http.CallServerInterceptor:
//
// /* compiled from: CallServerInterceptor.kt */
// /* loaded from: classes.dex */
// public final class CallServerInterceptor implements Interceptor {
//     private final boolean forWebSocket;
//     ...
//     public Response intercept(Interceptor.Chain chain) {
//         RealInterceptorChain realChain = chain;
//         Exchange exchange = realChain.exchange;
//         Request request = realChain.request;
//         ...
//         IOException sendRequestException = null;
//         try {
//             exchange.writeRequestHeaders(request);  <----
//             ...

// okhttp3.internal.connection.Exchange:
//
// /* compiled from: Exchange.kt */
// /* loaded from: classes.dex */
// public final class Exchange {
//     ...
//     public final void writeRequestHeaders(Request request) throws IOException {
//         Intrinsics.checkParameterIsNotNull(request, "request");
//         try {
//             this.eventListener.requestHeadersStart(this.call);
//             this.codec.writeRequestHeaders(request);  <----
//             this.eventListener.requestHeadersEnd(this.call, request);
//         } catch (IOException e) {
//             this.eventListener.requestFailed(this.call, e);
//             trackFailure(e);
//             throw e;
//         }
//     }
//     ...
// }

/* okhttp3.internal.connection.Exchange.writeRequestHeaders(okhttp3.Request r) hook. */
public class Exchange_writeRequestHeadersHook extends XC_MethodHook {
    private static final String HOOK_NAME = "Exchange.writeRequestHeaders";
    private static final AtomicLong NEXT_ID = new AtomicLong();

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

            CaptureEvent event = CaptureEvent.complete(
                    nextId(),
                    timestamp(),
                    packageName,
                    HOOK_NAME,
                    ReflectiveOkHttp.method(request),
                    ReflectiveOkHttp.url(request),
                    ReflectiveOkHttp.headers(request),
                    ReflectiveOkHttp.requestBody(request),
                    0L,
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
            logFailure("Exchange.writeRequestHeaders hook failed: %s", e);
        }
    }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        /* After hooked method. */
    }

    private static String nextId() {
        return "exchange-write-request-headers-" + NEXT_ID.incrementAndGet();
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(System.currentTimeMillis()));
    }

    private static void logFailure(String message, Throwable throwable) {
        try {
            NetTap.getXposedLogger().log(message, throwable);
        } catch (Throwable ignored) {
        }
    }
}
