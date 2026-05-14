package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class HookInstallerCoverageTest {

    @Before
    public void setUp() {
        InstallGuard.resetForTesting();
        RuntimeCaptureConfig.resetForTests();
        XposedBridge.resetForTesting();
    }

    @After
    public void tearDown() {
        InstallGuard.resetForTesting();
        RuntimeCaptureConfig.resetForTests();
        XposedBridge.resetForTesting();
    }

    @Test
    public void androidAsyncInstallHooksOnlyExecuteOverloadAcceptingRequest() {
        boolean installed = AndroidAsyncHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(1, XposedBridge.hookedMethods().size());
        assertEquals("execute", XposedBridge.hookedMethods().get(0).member.getName());
        assertTrue(InstallGuard.isInstalled("android-async", getClass().getClassLoader()));

        XC_MethodHook.MethodHookParam param = param(
                null,
                new com.koushikdutta.async.http.AsyncHttpRequest(),
                (com.koushikdutta.async.http.callback.HttpConnectCallback) (error, response) -> { }
        );
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod", param);
        assertTrue(param.args[1] instanceof com.koushikdutta.async.http.callback.HttpConnectCallback);
        ((com.koushikdutta.async.http.callback.HttpConnectCallback) param.args[1])
                .onConnectCompleted(null, new AsyncResponse());
    }

    @Test
    public void apacheHttp5InstallHooksOnlyDoExecuteWithAtLeastTwoArguments() {
        boolean installed = ApacheHttp5Hook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(1, XposedBridge.hookedMethods().size());
        assertEquals("doExecute", XposedBridge.hookedMethods().get(0).member.getName());
        assertTrue(InstallGuard.isInstalled("apache5", getClass().getClassLoader()));

        XC_MethodHook.MethodHookParam param = param(null, new ApacheHost(), new ApacheRequest());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod", param);
        param.setResult(new ApacheResponse());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod", param);
    }

    @Test
    public void grpcInstallHooksStartAndSendMessageAndRecordsListenerClose() throws Exception {
        boolean installed = GrpcCallInstaller.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(2, XposedBridge.hookedMethods().size());
        assertTrue(InstallGuard.isInstalled("grpc", getClass().getClassLoader()));

        io.grpc.internal.ClientCallImpl call = new io.grpc.internal.ClientCallImpl();
        io.grpc.ClientCall.Listener listener = new io.grpc.ClientCall.Listener() {
        };
        XC_MethodHook.MethodHookParam start = param(call, listener, new io.grpc.Metadata());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod", start);
        assertTrue(start.args[0] instanceof io.grpc.ClientCall.Listener);

        XC_MethodHook.MethodHookParam message = param(call, new GrpcMessage("request"));
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "beforeHookedMethod", message);

        io.grpc.ClientCall.Listener proxy = (io.grpc.ClientCall.Listener) start.args[0];
        proxy.onHeaders(new io.grpc.Metadata());
        proxy.onMessage(new GrpcMessage("response"));
        proxy.onClose(new GrpcStatus(), new io.grpc.Metadata());
    }

    @Test
    public void builderInterceptorHookInjectsOkHttpInterceptorAndRecordsSuccess() throws Exception {
        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
        BuilderInterceptorHook hook = new BuilderInterceptorHook(
                "pkg",
                okhttp3.Interceptor.class,
                okhttp3.Interceptor.class.getClassLoader()
        );

        invokeHook(hook, "beforeHookedMethod", param(builder));

        okhttp3.Interceptor interceptor = builder.build().interceptors().get(0);
        assertTrue(interceptor.toString().startsWith("NetTapInterceptor@"));
        okhttp3.Response response = interceptor.intercept(new FakeOkHttpChain(false));
        assertEquals(200, response.code());
    }

    @Test
    public void builderInterceptorHookWrapsUndeclaredCheckedFailure() throws Exception {
        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
        BuilderInterceptorHook hook = new BuilderInterceptorHook(
                "pkg",
                okhttp3.Interceptor.class,
                okhttp3.Interceptor.class.getClassLoader()
        );

        invokeHook(hook, "beforeHookedMethod", param(builder));

        okhttp3.Interceptor interceptor = builder.build().interceptors().get(0);
        try {
            interceptor.intercept(new FakeOkHttpChain(true));
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof java.io.IOException);
            return;
        }
        throw new AssertionError("expected RuntimeException");
    }

    @Test
    public void netTapSkipsOwnLauncherProcess() throws Throwable {
        de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lp =
                new de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam();
        lp.packageName = "xyz.winhok.nettap";
        lp.classLoader = getClass().getClassLoader();

        new NetTap().handleLoadPackage(lp);

        assertTrue(XposedBridge.hookedMethods().isEmpty());
    }

    @Test
    public void fuelInstallHooksOnlySingleArgumentExecuteRequest() {
        boolean installed = FuelHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(1, XposedBridge.hookedMethods().size());
        assertEquals("executeRequest", XposedBridge.hookedMethods().get(0).member.getName());
        assertTrue(InstallGuard.isInstalled("fuel", getClass().getClassLoader()));

        XC_MethodHook.MethodHookParam param = param(null, new FuelRequest());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod", param);
        param.setResult(new FuelResponse());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod", param);
    }

    @Test
    public void ktorInstallHooksEveryExecuteOverload() {
        boolean installed = KtorCioHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(2, XposedBridge.hookedMethods().size());
        assertEquals("execute", XposedBridge.hookedMethods().get(0).member.getName());
        assertEquals("execute", XposedBridge.hookedMethods().get(1).member.getName());
        assertTrue(InstallGuard.isInstalled("ktor-cio", getClass().getClassLoader()));

        XC_MethodHook.MethodHookParam param = param(null, new KtorRequestData(new KtorTextContent()));
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod", param);
        param.setResult(new KtorResponse());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod", param);

        XC_MethodHook.MethodHookParam bytesParam = param(null, new KtorRequestData(new KtorByteArrayContent()));
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "beforeHookedMethod", bytesParam);
        bytesParam.setThrowable(new IllegalStateException("network failed"));
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "afterHookedMethod", bytesParam);
    }

    @Test
    public void volleyInstallHooksBasicNetworkPerformRequest() {
        boolean installed = VolleyHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(1, XposedBridge.hookedMethods().size());
        assertEquals("performRequest", XposedBridge.hookedMethods().get(0).member.getName());
        assertTrue(InstallGuard.isInstalled("volley", getClass().getClassLoader()));

        XC_MethodHook.MethodHookParam param = param(null, new VolleyRequest());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod", param);
        param.setResult(new VolleyResponse());
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod", param);
    }

    @Test
    public void httpUrlConnectionInstallHooksAllRequestLifecycleMethods() throws Exception {
        boolean installed = HttpURLConnectionHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(6, XposedBridge.hookedMethods().size());
        assertTrue(InstallGuard.isInstalled("hurl", getClass().getClassLoader()));

        FakeConnection connection = new FakeConnection();
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "beforeHookedMethod",
                param(connection));

        XC_MethodHook.MethodHookParam output = param(connection);
        output.setResult(new java.io.ByteArrayOutputStream());
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "afterHookedMethod", output);
        ((java.io.OutputStream) output.getResult()).write("request".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));

        XC_MethodHook.MethodHookParam input = param(connection);
        input.setResult(new java.io.ByteArrayInputStream("response".getBytes(
                java.nio.charset.StandardCharsets.UTF_8)));
        invokeHook(XposedBridge.hookedMethods().get(2).callback, "afterHookedMethod", input);
        while (((java.io.InputStream) input.getResult()).read() != -1) {
            // Drain to trigger TeeInputStream completion.
        }

        XC_MethodHook.MethodHookParam code = param(connection);
        code.setResult(200);
        invokeHook(XposedBridge.hookedMethods().get(4).callback, "afterHookedMethod", code);
        invokeHook(XposedBridge.hookedMethods().get(5).callback, "beforeHookedMethod",
                param(connection));
    }

    @Test
    public void cronetUrlRequestHooksLifecycleAndBodyChunks() {
        boolean installed = CronetUrlRequestHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(8, XposedBridge.hookedMethods().size());

        org.chromium.net.impl.CronetUrlRequest request =
                new org.chromium.net.impl.CronetUrlRequest("https://example.com/cronet");
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod",
                param(request, "https://example.com/cronet"));
        invokeHook(XposedBridge.hookedMethods().get(7).callback, "beforeHookedMethod",
                param(request, "POST"));
        java.nio.ByteBuffer body = java.nio.ByteBuffer.wrap("request".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        CronetUrlRequestHook.attachRequestBodyChunk(request, body, 7, 0, 7);
        assertTrue(CronetUploadDataProviderHook.install("pkg", getClass().getClassLoader()));
        assertEquals(10, XposedBridge.hookedMethods().size());
        java.nio.ByteBuffer uploadBuffer = java.nio.ByteBuffer.allocate(16);
        uploadBuffer.put("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.chromium.net.impl.CronetUploadDataStream stream =
                new org.chromium.net.impl.CronetUploadDataStream(request, uploadBuffer);
        invokeHook(XposedBridge.hookedMethods().get(8).callback, "afterHookedMethod",
                param(stream, request, uploadBuffer));
        invokeHook(XposedBridge.hookedMethods().get(9).callback, "beforeHookedMethod",
                param(stream));
        java.nio.ByteBuffer response = java.nio.ByteBuffer.wrap("response".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        invokeHook(XposedBridge.hookedMethods().get(6).callback, "beforeHookedMethod",
                param(request, response, 8, 0, 8));
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "beforeHookedMethod",
                param(request, 200, "OK",
                        new String[] { "content-type", "text/plain" }, null, "h2", "proxy"));
        invokeHook(XposedBridge.hookedMethods().get(3).callback, "beforeHookedMethod",
                param(request));

        org.chromium.net.impl.CronetUrlRequest redirected =
                new org.chromium.net.impl.CronetUrlRequest("https://example.com/old");
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod",
                param(redirected, "https://example.com/old"));
        invokeHook(XposedBridge.hookedMethods().get(2).callback, "beforeHookedMethod",
                param(redirected, "https://example.com/new", 302, "Found",
                        new String[] { "location", "https://example.com/new" },
                        null, "h3", "proxy2"));
        invokeHook(XposedBridge.hookedMethods().get(4).callback, "beforeHookedMethod",
                param(redirected, 7, 8, 9, "network"));

        org.chromium.net.impl.CronetUrlRequest canceled =
                new org.chromium.net.impl.CronetUrlRequest("https://example.com/cancel");
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod",
                param(canceled));
        invokeHook(XposedBridge.hookedMethods().get(7).callback, "beforeHookedMethod",
                param(canceled, (Object) null));
        invokeHook(XposedBridge.hookedMethods().get(6).callback, "beforeHookedMethod",
                param(canceled));
        invokeHook(XposedBridge.hookedMethods().get(6).callback, "beforeHookedMethod",
                param(canceled, "not-a-buffer", 1, 0, 1));
        invokeHook(XposedBridge.hookedMethods().get(5).callback, "beforeHookedMethod",
                param(canceled));

        invokeHook(XposedBridge.hookedMethods().get(1).callback, "beforeHookedMethod",
                param(new Object(), 200));
        invokeHook(XposedBridge.hookedMethods().get(6).callback, "beforeHookedMethod",
                param(new Object(), response, 1, 0, 1));
        invokeHook(XposedBridge.hookedMethods().get(9).callback, "beforeHookedMethod",
                param(new Object()));
        java.nio.ByteBuffer emptyUpload = java.nio.ByteBuffer.allocate(8);
        org.chromium.net.impl.CronetUploadDataStream emptyStream =
                new org.chromium.net.impl.CronetUploadDataStream(canceled, emptyUpload);
        invokeHook(XposedBridge.hookedMethods().get(8).callback, "afterHookedMethod",
                param(emptyStream, canceled, emptyUpload));
        invokeHook(XposedBridge.hookedMethods().get(9).callback, "beforeHookedMethod",
                param(emptyStream));
    }

    @Test
    public void cronetBidirectionalStreamHooksHeadersReadWriteAndFailure() {
        boolean installed = CronetBidirectionalStreamHook.install("pkg", getClass().getClassLoader());

        assertTrue(installed);
        assertEquals(7, XposedBridge.hookedMethods().size());

        org.chromium.net.impl.CronetBidirectionalStream stream =
                new org.chromium.net.impl.CronetBidirectionalStream("https://example.com/bidi");
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod",
                param(stream, "https://example.com/bidi"));
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "beforeHookedMethod",
                param(stream, new BidiInfo()));
        java.nio.ByteBuffer read = java.nio.ByteBuffer.allocate(16);
        read.put("response".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        invokeHook(XposedBridge.hookedMethods().get(2).callback, "beforeHookedMethod",
                param(stream, read));
        java.nio.ByteBuffer write = java.nio.ByteBuffer.allocate(16);
        write.put("request".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        invokeHook(XposedBridge.hookedMethods().get(3).callback, "beforeHookedMethod",
                param(stream, write));
        invokeHook(XposedBridge.hookedMethods().get(5).callback, "beforeHookedMethod",
                param(stream, null, "boom"));

        org.chromium.net.impl.CronetBidirectionalStream success =
                new org.chromium.net.impl.CronetBidirectionalStream("https://example.com/bidi-success");
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod",
                param(success, Integer.valueOf(1), "https://example.com/bidi-success"));
        invokeHook(XposedBridge.hookedMethods().get(1).callback, "beforeHookedMethod",
                param(success));
        invokeHook(XposedBridge.hookedMethods().get(2).callback, "beforeHookedMethod",
                param(success, "not-a-buffer"));
        invokeHook(XposedBridge.hookedMethods().get(3).callback, "beforeHookedMethod",
                param(success));
        invokeHook(XposedBridge.hookedMethods().get(4).callback, "beforeHookedMethod",
                param(success));

        org.chromium.net.impl.CronetBidirectionalStream canceled =
                new org.chromium.net.impl.CronetBidirectionalStream("https://example.com/bidi-cancel");
        invokeHook(XposedBridge.hookedMethods().get(0).callback, "afterHookedMethod",
                param(canceled));
        invokeHook(XposedBridge.hookedMethods().get(5).callback, "beforeHookedMethod",
                param(canceled));
        invokeHook(XposedBridge.hookedMethods().get(6).callback, "beforeHookedMethod",
                param(canceled));
    }

    @Test
    public void cronetKeylogMergesExperimentalOptionsConservatively() {
        assertEquals("{\"ssl_key_log_file\":\"/tmp/key.log\"}",
                CronetKeyLogHook.mergeKeyLogPath(null, "/tmp/key.log"));
        assertEquals("{\"ssl_key_log_file\":\"/tmp/key.log\",\"QUIC\":{}}",
                CronetKeyLogHook.mergeKeyLogPath("{\"QUIC\":{}}", "/tmp/key.log"));
        assertEquals("{\"ssl_key_log_file\":\"old\"}",
                CronetKeyLogHook.mergeKeyLogPath("{\"ssl_key_log_file\":\"old\"}", "/tmp/key.log"));
        assertEquals("{\"ssl_key_log_file\":\"C:\\\\tmp\\\\a\\\"b.log\"}",
                CronetKeyLogHook.mergeKeyLogPath("broken", "C:\\tmp\\a\"b.log"));
    }

    @Test
    public void nullClassLoaderInstallersReturnFalseWithoutHooking() {
        assertFalse(AndroidAsyncHook.install("pkg", null));
        assertFalse(ApacheHttp5Hook.install("pkg", null));
        assertFalse(CronetKeyLogHook.install("pkg", null));
        assertFalse(CronetUploadDataProviderHook.install("pkg", null));
        assertFalse(TlsKeyLogHook.install("pkg", null));
        assertTrue(XposedBridge.hookedMethods().isEmpty());
    }

    private static XC_MethodHook.MethodHookParam param(Object thisObject, Object... args) {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = thisObject;
        param.args = args;
        return param;
    }

    private static void invokeHook(XC_MethodHook hook, String methodName,
                                   XC_MethodHook.MethodHookParam param) {
        Class<?> current = hook.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, XC_MethodHook.MethodHookParam.class);
                method.setAccessible(true);
                method.invoke(hook, param);
                return;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("hook method not found: " + methodName);
    }

    public static final class AsyncResponse {
        public int code() {
            return 202;
        }

        public String message() {
            return "Accepted";
        }

        public com.koushikdutta.async.http.AsyncHttpRequest.Headers headers() {
            return new com.koushikdutta.async.http.AsyncHttpRequest.Headers();
        }
    }

    public static final class ApacheHost {
        @Override
        public String toString() {
            return "https://example.com";
        }
    }

    public static final class ApacheRequest {
        public String getRequestUri() {
            return "/apache";
        }

        public String getMethod() {
            return "PUT";
        }

        public Object[] getHeaders() {
            return new Object[] { new Header("X-Apache", "yes") };
        }

        public ApacheEntity getEntity() {
            return new ApacheEntity("request-body");
        }
    }

    public static final class ApacheResponse {
        public int getCode() {
            return 201;
        }

        public String getReasonPhrase() {
            return "Created";
        }

        public Object[] getHeaders() {
            return new Object[] { new Header("X-Response", "yes") };
        }

        public ApacheEntity getEntity() {
            return new ApacheEntity("response-body");
        }
    }

    public static final class Header {
        private final String name;
        private final String value;

        Header(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class ApacheEntity {
        private final String body;

        ApacheEntity(String body) {
            this.body = body;
        }

        public boolean isRepeatable() {
            return true;
        }

        public String getContentType() {
            return "text/plain";
        }

        public long getContentLength() {
            return body.length();
        }

        public java.io.InputStream getContent() {
            return new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static final class FuelRequest {
        public String getUrl() {
            return "https://example.com/fuel";
        }

        public String getMethod() {
            return "GET";
        }

        public LinkedHashMap<String, List<String>> getHeaders() {
            LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
            headers.put("X-Fuel", Arrays.asList("a", "b"));
            return headers;
        }

        public FuelBody getBody() {
            return new FuelBody("fuel-request");
        }
    }

    public static final class FuelResponse {
        public int getStatusCode() {
            return 200;
        }

        public String getResponseMessage() {
            return "OK";
        }

        public LinkedHashMap<String, String> getHeaders() {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Fuel-Response", "yes");
            return headers;
        }

        public byte[] getData() {
            return "fuel-response".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static final class FuelBody {
        private final String value;

        FuelBody(String value) {
            this.value = value;
        }

        public byte[] toByteArray() {
            return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static final class KtorRequestData {
        private final Object body;

        KtorRequestData(Object body) {
            this.body = body;
        }

        public String getUrl() {
            return "https://example.com/ktor";
        }

        public KtorMethod getMethod() {
            return new KtorMethod();
        }

        public KtorHeaders getHeaders() {
            return new KtorHeaders();
        }

        public Object getBody() {
            return body;
        }
    }

    public static final class KtorMethod {
        public String getValue() {
            return "PATCH";
        }
    }

    public static final class KtorHeaders {
        public Iterable<java.util.Map.Entry<String, List<String>>> entries() {
            return Collections.singletonList(
                    new AbstractMap.SimpleEntry<>("X-Ktor", Arrays.asList("a", "b")));
        }
    }

    public static final class KtorTextContent {
        public String getText() {
            return "ktor-text";
        }
    }

    public static final class KtorByteArrayContent {
        public byte[] bytes() {
            return "ktor-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static final class KtorResponse {
        public KtorStatus getStatusCode() {
            return new KtorStatus();
        }

        public KtorHeaders getHeaders() {
            return new KtorHeaders();
        }
    }

    public static final class KtorStatus {
        public int getValue() {
            return 204;
        }

        public String getDescription() {
            return "No Content";
        }
    }

    public static final class VolleyRequest extends com.android.volley.Request {
        public String getUrl() {
            return "https://example.com/volley";
        }

        public int getMethod() {
            return 1;
        }

        public LinkedHashMap<String, String> getHeaders() {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Volley", "yes");
            return headers;
        }

        public byte[] getBody() {
            return "volley-request".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static final class VolleyResponse {
        public int statusCode = 200;
        public List<Header> allHeaders = Collections.singletonList(new Header("X-Volley-Response", "yes"));
        public byte[] data = "volley-response".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static final class GrpcMessage {
        private final String value;

        GrpcMessage(String value) {
            this.value = value;
        }

        public byte[] toByteArray() {
            return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static final class GrpcStatus {
        public GrpcCode getCode() {
            return new GrpcCode();
        }

        public String getDescription() {
            return "OK";
        }
    }

    public static final class GrpcCode {
        public int value() {
            return 0;
        }
    }

    public static final class FakeConnection extends HttpURLConnection {
        FakeConnection() throws Exception {
            super(new URL("https://example.com/hurl"));
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public java.util.Map<String, List<String>> getRequestProperties() {
            return Collections.singletonMap("X-Hurl", Arrays.asList("a", "b"));
        }

        @Override
        public String getResponseMessage() {
            return "OK";
        }

        @Override
        public java.util.Map<String, List<String>> getHeaderFields() {
            return Collections.singletonMap("X-Hurl-Response", Collections.singletonList("yes"));
        }
    }

    public static final class BidiInfo {
        public java.util.Map<String, String> getAllHeaders() {
            return Collections.singletonMap("content-type", "text/plain");
        }

        public int getHttpStatusCode() {
            return 500;
        }
    }

    public static final class FakeOkHttpChain implements okhttp3.Interceptor.Chain {
        private final boolean fail;
        private final okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://example.com/okhttp")
                .get()
                .build();

        FakeOkHttpChain(boolean fail) {
            this.fail = fail;
        }

        @Override
        public okhttp3.Request request() {
            return request;
        }

        @Override
        public okhttp3.Response proceed(okhttp3.Request request) throws java.io.IOException {
            if (fail) {
                throw new java.io.IOException("boom");
            }
            return new okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(
                            "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            okhttp3.MediaType.get("text/plain")
                    ))
                    .build();
        }

        @Override
        public okhttp3.Connection connection() {
            return null;
        }

        @Override
        public okhttp3.Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public okhttp3.Interceptor.Chain withConnectTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public okhttp3.Interceptor.Chain withReadTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public okhttp3.Interceptor.Chain withWriteTimeout(int timeout, java.util.concurrent.TimeUnit unit) {
            return this;
        }
    }
}
