package xyz.winhok.nettap;

import java.util.Arrays;
import java.util.List;

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

    private interface HookFn {
        boolean install(String packageName, ClassLoader classLoader);
    }

    private static final class HookSpec {
        final String tag;
        final HookFn installer;

        HookSpec(String tag, HookFn installer) {
            this.tag = tag;
            this.installer = installer;
        }

        boolean tryInstall(LoadPackageParam lp) {
            try {
                boolean installed = installer.install(lp.packageName, lp.classLoader);
                NetTap.getXposedLogger().log(
                        "hook attempt result: %s installed=%s", tag, installed);
                return installed;
            } catch (Throwable e) {
                NetTap.getXposedLogger().log("%s install failed: %s", tag, e);
                return false;
            }
        }
    }

    private static final List<HookSpec> STANDARD_HOOKS = Arrays.asList(
            new HookSpec("cronet-url",    CronetUrlRequestHook::install),
            new HookSpec("cronet-bidi",   CronetBidirectionalStreamHook::install),
            new HookSpec("cronet-upload", CronetUploadDataProviderHook::install),
            new HookSpec("grpc",          GrpcCallInstaller::install),
            new HookSpec("hurl",          HttpURLConnectionHook::install),
            new HookSpec("volley",        VolleyHook::install),
            new HookSpec("fuel",          FuelHook::install),
            new HookSpec("apache5",       ApacheHttp5Hook::install),
            new HookSpec("ktor-cio",      KtorCioHook::install),
            new HookSpec("android-async", AndroidAsyncHook::install),
            new HookSpec("tls-keylog",    TlsKeyLogHook::install),
            new HookSpec("cronet-keylog", CronetKeyLogHook::install)
    );

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        NetTap.getXposedLogger().log(
                "module loaded into: %s classLoader=%s process=%s",
                lpparam.packageName,
                String.valueOf(lpparam.classLoader),
                readStringField(lpparam, "processName"));
        if ("xyz.winhok.nettap".equals(lpparam.packageName)) {
            NetTap.getXposedLogger().log("skip hooking module UI process");
            return;
        }
        RuntimeCaptureConfig.refreshFromXSharedPreferences();

        DiscoveredOkHttp discovered = ShadedOkHttpDiscovery.discover(
                lpparam.packageName, lpparam.classLoader);
        NetTap.getXposedLogger().log(
                "okhttp discovery: package=%s hasAny=%s detail=%s",
                lpparam.packageName,
                discovered != null && discovered.hasAny(),
                String.valueOf(discovered));

        boolean anyInstalled = installOkHttpHooks(lpparam, discovered);
        NetTap.getXposedLogger().log(
                "hook attempt result: %s installed=%s", OKHTTP_HOOK_ID, anyInstalled);
        for (HookSpec spec : STANDARD_HOOKS) {
            anyInstalled |= spec.tryInstall(lpparam);
        }

        if (!anyInstalled) {
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

    private static String readStringField(Object target, String fieldName) {
        try {
            Object value = target.getClass().getField(fieldName).get(target);
            return String.valueOf(value);
        } catch (Throwable ignored) {
            return "unknown";
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

        if (RuntimeCaptureConfig.isBuilderInterceptorHookEnabled()
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
