package xyz.winhok.nettap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XposedHelpers;

import xyz.winhok.nettap.ShadedClassRegistry.DiscoveredOkHttp;


/* Log all HTTP requests across OkHttp, Cronet, gRPC-over-OkHttp, and java.net. */
public class NetTap implements IXposedHookLoadPackage {
    private static String moduleName = "NetTap";
    private static final String OKHTTP_HOOK_ID = "okhttp";
    private static XposedLogger xposedLogger;

    public static synchronized XposedLogger getXposedLogger() {
        if (xposedLogger == null) {
            xposedLogger = new XposedLogger(moduleName);
        }
        return xposedLogger;
    }

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        NetTap.getXposedLogger().log("module loaded into: %s", lpparam.packageName);

        DiscoveredOkHttp discovered = ShadedOkHttpDiscovery.discover(
                lpparam.packageName, lpparam.classLoader);

        boolean okhttpInstalled = installOkHttpHooks(lpparam, discovered);

        boolean cronetInstalled = installCronetUrlRequestHook(lpparam);
        boolean cronetBidiInstalled = installCronetBidiHook(lpparam);
        installCronetUploadDataProviderHook(lpparam);

        boolean grpcInstalled = installGrpcHook(lpparam);

        boolean hurlInstalled = installHttpURLConnectionHook(lpparam);

        boolean volleyInstalled = installVolleyHook(lpparam);
        boolean fuelInstalled = installFuelHook(lpparam);
        boolean apache5Installed = installApacheHttp5Hook(lpparam);
        boolean ktorCioInstalled = installKtorCioHook(lpparam);
        boolean androidAsyncInstalled = installAndroidAsyncHook(lpparam);

        boolean tlsKeyLogInstalled = installTlsKeyLogHook(lpparam);
        boolean cronetKeyLogInstalled = installCronetKeyLogHook(lpparam);

        if (!okhttpInstalled && !cronetInstalled && !cronetBidiInstalled
                && !grpcInstalled && !hurlInstalled
                && !volleyInstalled && !fuelInstalled && !apache5Installed
                && !ktorCioInstalled && !androidAsyncInstalled
                && !tlsKeyLogInstalled && !cronetKeyLogInstalled) {
            NetTap.getXposedLogger().log(
                    "no HTTP-family stack detected in %s — skipping", lpparam.packageName);
            return;
        }

        NetTap.getXposedLogger().log(
                "HTTP capture active in %s\n" +
                "View log: adb shell su -c cat /data/data/%s/files/%s",
                lpparam.packageName, lpparam.packageName, CaptureConfig.CAPTURE_FILENAME);
    }

    private boolean installOkHttpHooks(final LoadPackageParam lpparam, DiscoveredOkHttp discovered) {
        if (discovered == null || !discovered.hasAny()) {
            NetTap.getXposedLogger().log(
                    "no OkHttp classes found in %s (neither stock nor shaded)",
                    lpparam.packageName);
            return false;
        }

        // Shared OkHttp install latch across all four layers. handleLoadPackage may
        // fire more than once for the same classloader; without the latch every
        // request would produce 2-4 duplicate JSONL events (one per layer that
        // installed). Priority ladder (RealCall > Chain > Builder > Exchange)
        // is preserved inside the latch.
        java.util.concurrent.atomic.AtomicBoolean result =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            boolean ran = InstallGuard.installOncePerLoader(
                    OKHTTP_HOOK_ID,
                    lpparam.classLoader,
                    () -> result.set(installOkHttpHooksInner(lpparam, discovered)));
            if (!ran) {
                return InstallGuard.isInstalled(OKHTTP_HOOK_ID, lpparam.classLoader);
            }
            return result.get();
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("okhttp install wrapper failed: %s", e);
            return false;
        }
    }

    private boolean installOkHttpHooksInner(final LoadPackageParam lpparam, DiscoveredOkHttp discovered) {
        boolean realCallHookInstalled = installOkHttp4RealCallResponseHook(lpparam);
        if (!realCallHookInstalled) {
            realCallHookInstalled = installOkHttp3RealCallResponseHook(lpparam);
        }
        if (realCallHookInstalled) {
            try {
                MetricsReporter.incInstalled(
                        lpparam.packageName, MetricsReporter.LAYER_OKHTTP_REALCALL);
            } catch (Throwable ignored) {
            }
            return true;
        }

        boolean chainInstalled = installRealInterceptorChainHook(lpparam, discovered);
        if (chainInstalled) {
            return true;
        }

        if (CaptureConfig.ENABLE_BUILDER_INTERCEPTOR_HOOK
                && installBuilderInterceptorHook(lpparam)) {
            return true;
        }

        return installExchangeWriteRequestHeadersHook(lpparam);
    }

    private boolean installRealInterceptorChainHook(
            final LoadPackageParam lpparam,
            DiscoveredOkHttp discovered
    ) {
        Class<?> chain = discovered.realInterceptorChain;
        if (chain == null) {
            return false;
        }
        Class<?> requestType = discovered.request;
        if (requestType == null) {
            return false;
        }
        try {
            XposedHelpers.findAndHookMethod(
                    chain, "proceed", requestType,
                    new RealInterceptorChainProceedHook(lpparam.packageName));
            NetTap.getXposedLogger().log(
                    "installed hook: %s.proceed(%s)", chain.getName(), requestType.getName());
            try {
                MetricsReporter.incInstalled(
                        lpparam.packageName, MetricsReporter.LAYER_OKHTTP_CHAIN);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log(
                    "failed to install %s.proceed hook: %s", chain.getName(), e);
            return false;
        }
    }

    private boolean installCronetUrlRequestHook(final LoadPackageParam lpparam) {
        return CronetUrlRequestHook.install(lpparam.packageName, lpparam.classLoader);
    }

    private boolean installCronetBidiHook(final LoadPackageParam lpparam) {
        try {
            return CronetBidirectionalStreamHook.install(
                    lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("cronet-bidi install failed: %s", e);
            return false;
        }
    }

    private boolean installCronetUploadDataProviderHook(final LoadPackageParam lpparam) {
        try {
            return CronetUploadDataProviderHook.install(
                    lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("cronet-upload install failed: %s", e);
            return false;
        }
    }

    private boolean installGrpcHook(final LoadPackageParam lpparam) {
        try {
            return GrpcCallInstaller.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("grpc install failed: %s", e);
            return false;
        }
    }

    private boolean installHttpURLConnectionHook(final LoadPackageParam lpparam) {
        try {
            return HttpURLConnectionHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("hurl install failed: %s", e);
            return false;
        }
    }

    private boolean installVolleyHook(final LoadPackageParam lpparam) {
        try {
            return VolleyHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("volley install failed: %s", e);
            return false;
        }
    }

    private boolean installFuelHook(final LoadPackageParam lpparam) {
        try {
            return FuelHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("fuel install failed: %s", e);
            return false;
        }
    }

    private boolean installApacheHttp5Hook(final LoadPackageParam lpparam) {
        try {
            return ApacheHttp5Hook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("apache5 install failed: %s", e);
            return false;
        }
    }

    private boolean installKtorCioHook(final LoadPackageParam lpparam) {
        try {
            return KtorCioHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("ktor-cio install failed: %s", e);
            return false;
        }
    }

    private boolean installAndroidAsyncHook(final LoadPackageParam lpparam) {
        try {
            return AndroidAsyncHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("android-async install failed: %s", e);
            return false;
        }
    }

    private boolean installTlsKeyLogHook(final LoadPackageParam lpparam) {
        try {
            return TlsKeyLogHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("tls-keylog install failed: %s", e);
            return false;
        }
    }

    private boolean installCronetKeyLogHook(final LoadPackageParam lpparam) {
        try {
            return CronetKeyLogHook.install(lpparam.packageName, lpparam.classLoader);
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("cronet-keylog install failed: %s", e);
            return false;
        }
    }

    private boolean installOkHttp4RealCallResponseHook(final LoadPackageParam lpparam) {
        return installRealCallResponseHook(
                lpparam,
                "okhttp3.internal.connection.RealCall",
                "getResponseWithInterceptorChain$okhttp",
                "RealCall.getResponseWithInterceptorChain$okhttp"
        );
    }

    private boolean installOkHttp3RealCallResponseHook(final LoadPackageParam lpparam) {
        return installRealCallResponseHook(
                lpparam,
                "okhttp3.RealCall",
                "getResponseWithInterceptorChain",
                "RealCall.getResponseWithInterceptorChain"
        );
    }

    private boolean installRealCallResponseHook(
            final LoadPackageParam lpparam,
            String className,
            String methodName,
            String hookName
    ) {
        try {
            Class<?> RealCall = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (RealCall == null) {
                NetTap.getXposedLogger().log("hook unavailable: %s", className);
                return false;
            }

            XposedHelpers.findAndHookMethod(
                    RealCall,
                    methodName,
                    new RealCallResponseHook(lpparam.packageName, hookName)
            );
            NetTap.getXposedLogger().log(
                    "installed hook: %s.%s()", className, methodName);
            return true;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log(
                    "failed to install %s.%s hook: %s", className, methodName, e);
            return false;
        }
    }

    private boolean installExchangeWriteRequestHeadersHook(final LoadPackageParam lpparam) {
        try {
            Class<?> Exchange = XposedHelpers.findClassIfExists(
                    "okhttp3.internal.connection.Exchange", lpparam.classLoader);
            if (Exchange == null) {
                NetTap.getXposedLogger().log(
                        "hook unavailable: okhttp3.internal.connection.Exchange");
                return false;
            }

            XposedHelpers.findAndHookMethod(
                    Exchange,
                    "writeRequestHeaders",
                    "okhttp3.Request",
                    new Exchange_writeRequestHeadersHook(lpparam.packageName)
            );
            NetTap.getXposedLogger().log(
                    "installed hook: okhttp3.internal.connection.Exchange.writeRequestHeaders(okhttp3.Request)");
            try {
                MetricsReporter.incInstalled(
                        lpparam.packageName, MetricsReporter.LAYER_OKHTTP_EXCHANGE);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log(
                    "failed to install Exchange.writeRequestHeaders hook: %s", e);
            return false;
        }
    }

    private boolean installBuilderInterceptorHook(final LoadPackageParam lpparam) {
        try {
            Class<?> Builder = XposedHelpers.findClassIfExists(
                    "okhttp3.OkHttpClient$Builder", lpparam.classLoader);
            if (Builder == null) {
                NetTap.getXposedLogger().log(
                        "hook unavailable: okhttp3.OkHttpClient$Builder");
                return false;
            }

            Class<?> Interceptor = XposedHelpers.findClassIfExists(
                    "okhttp3.Interceptor", lpparam.classLoader);
            if (Interceptor == null) {
                NetTap.getXposedLogger().log(
                        "hook unavailable: okhttp3.Interceptor");
                return false;
            }

            XposedHelpers.findAndHookMethod(
                    Builder,
                    "build",
                    new BuilderInterceptorHook(lpparam.packageName, Interceptor, lpparam.classLoader)
            );
            NetTap.getXposedLogger().log(
                    "installed hook: okhttp3.OkHttpClient$Builder.build()");
            try {
                MetricsReporter.incInstalled(
                        lpparam.packageName, MetricsReporter.LAYER_OKHTTP_BUILDER);
            } catch (Throwable ignored) {
            }
            return true;
        } catch (Throwable e) {
            NetTap.getXposedLogger().log(
                    "failed to install OkHttpClient.Builder interceptor hook: %s", e);
            return false;
        }
    }
}
