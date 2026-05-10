package xyz.winhok.nettap;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Routes Cronet's BoringSSL TLS secrets into the same NSS-format key log file
 * that {@link TlsKeyLogHook} writes. Cronet embeds BoringSSL and supports SSL
 * key logging via the {@code experimental_options} JSON on
 * {@code CronetEngine.Builder}: set {@code "ssl_key_log_file"} to a writable
 * path and BoringSSL appends CLIENT_RANDOM / TRAFFIC_SECRET lines at
 * handshake time for both TCP (TLS 1.3) and UDP (QUIC) flows.
 *
 * <p>We hook {@code CronetEngine$Builder.setExperimentalOptions(String)} and
 * {@code CronetEngineBuilderImpl.build()} so that whichever builder the app
 * uses, {@code ssl_key_log_file} lands in the final engine config. Existing
 * experimental options are merged, not overwritten.
 */
public final class CronetKeyLogHook {

    private static final String BUILDER = "org.chromium.net.CronetEngine$Builder";
    private static final String BUILDER_IMPL = "org.chromium.net.impl.CronetEngineBuilderImpl";

    private CronetKeyLogHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null || !CaptureConfig.ENABLE_CRONET_QUIC_KEYLOG) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("cronet-keylog", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("cronet-keylog install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        String path = TlsKeyLogHook.resolveKeyLogPath();
        if (path == null) {
            throw new RuntimeException("cronet-keylog-no-ctx");
        }
        int surfaces = 0;
        surfaces += hookBuilderSetExperimentalOptions(classLoader, path) ? 1 : 0;
        surfaces += hookBuilderImplBuild(classLoader, path) ? 1 : 0;
        if (surfaces == 0) {
            throw new RuntimeException("cronet-keylog-no-builder");
        }
        NetTap.getXposedLogger().log(
                "cronet-keylog: routed %d builder surface(s) to %s for %s",
                surfaces, path, packageName);
        MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_CRONET_KEYLOG);
    }

    private static boolean hookBuilderSetExperimentalOptions(ClassLoader classLoader, String path) {
        Class<?> builder = XposedHelpers.findClassIfExists(BUILDER, classLoader);
        if (builder == null) {
            return false;
        }
        int hooked = 0;
        for (Method m : builder.getDeclaredMethods()) {
            if (!"setExperimentalOptions".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 1 || p[0] != String.class) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length == 0) {
                            return;
                        }
                        String original = param.args[0] instanceof String
                                ? (String) param.args[0] : null;
                        param.args[0] = mergeKeyLogPath(original, path);
                    }
                });
                hooked++;
            } catch (Throwable e) {
                NetTap.getXposedLogger().logSafe(
                        "cronet-keylog setExperimentalOptions hook failed: %s", e);
            }
        }
        return hooked > 0;
    }

    /**
     * Some apps skip {@code setExperimentalOptions} entirely. Hook
     * {@code CronetEngineBuilderImpl.build()} and inject the option via the
     * private {@code experimentalOptions} field before the native builder
     * hands off to BoringSSL.
     */
    private static boolean hookBuilderImplBuild(ClassLoader classLoader, String path) {
        Class<?> impl = XposedHelpers.findClassIfExists(BUILDER_IMPL, classLoader);
        if (impl == null) {
            return false;
        }
        int hooked = 0;
        for (Method m : impl.getDeclaredMethods()) {
            if (!"build".equals(m.getName()) || m.getParameterTypes().length != 0) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object current = null;
                            try {
                                current = XposedHelpers.getObjectField(
                                        param.thisObject, "mExperimentalOptions");
                            } catch (Throwable ignored) {
                            }
                            if (current == null) {
                                try {
                                    current = XposedHelpers.getObjectField(
                                            param.thisObject, "experimentalOptions");
                                } catch (Throwable ignored) {
                                }
                            }
                            String merged = mergeKeyLogPath(
                                    current == null ? null : current.toString(), path);
                            try {
                                XposedHelpers.setObjectField(
                                        param.thisObject, "mExperimentalOptions", merged);
                            } catch (Throwable mFieldErr) {
                                try {
                                    XposedHelpers.setObjectField(
                                            param.thisObject, "experimentalOptions", merged);
                                } catch (Throwable altErr) {
                                    NetTap.getXposedLogger().logSafe(
                                            "cronet-keylog neither m/experimentalOptions "
                                                    + "field settable: %s / %s",
                                            mFieldErr, altErr);
                                }
                            }
                        } catch (Throwable e) {
                            NetTap.getXposedLogger().logSafe(
                                    "cronet-keylog build inject failed: %s", e);
                        }
                    }
                });
                hooked++;
            } catch (Throwable e) {
                NetTap.getXposedLogger().logSafe(
                        "cronet-keylog CronetEngineBuilderImpl.build hook failed: %s", e);
            }
        }
        return hooked > 0;
    }

    /**
     * Conservative JSON merge: add {@code "ssl_key_log_file": "<path>"} at
     * the top level. If the app already supplies an experimental options
     * blob, splice our key in before the closing brace and preserve their
     * payload byte-for-byte. Malformed input falls back to the minimal
     * object so keylogging wins over their broken blob.
     */
    static String mergeKeyLogPath(String original, String path) {
        String escapedPath = path.replace("\\", "\\\\").replace("\"", "\\\"");
        String inject = "\"ssl_key_log_file\":\"" + escapedPath + "\"";
        if (original == null) {
            return "{" + inject + "}";
        }
        String trimmed = original.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return "{" + inject + "}";
        }
        if (trimmed.contains("\"ssl_key_log_file\"")) {
            return original;
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return "{" + inject + "}";
        }
        return "{" + inject + "," + body + "}";
    }

    private static String resolveKeyLogPath() {
        try {
            Object context = xdroid.core.Global.getContext();
            if (context instanceof android.content.Context) {
                java.io.File f = ((android.content.Context) context).getFileStreamPath(
                        CaptureConfig.TLS_KEYLOG_FILENAME);
                return f == null ? null : f.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
