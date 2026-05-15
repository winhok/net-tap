package xyz.winhok.nettap;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

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
 * canonical {@code CronetEngineBuilderImpl} build methods so that
 * {@code ssl_key_log_file} lands in the final engine config. Existing
 * experimental options are merged, not overwritten.
 */
public final class CronetKeyLogHook {

    private CronetKeyLogHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null || !RuntimeCaptureConfig.isCronetQuicKeylogEnabled()) {
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
        List<Class<?>> builders = CronetCandidates.resolveAllWithSuffix(
                classLoader, CronetCandidates.CRONET_ENGINE_BUILDER_SUFFIX);
        for (Class<?> builder : builders) {
            surfaces += hookBuilderSetExperimentalOptions(builder, path) ? 1 : 0;
        }
        List<Class<?>> builderImpls = CronetCandidates.resolveAllWithSuffix(
                classLoader, CronetCandidates.CRONET_ENGINE_BUILDER_IMPL_SUFFIX);
        for (Class<?> builderImpl : builderImpls) {
            surfaces += hookBuilderImplBuild(builderImpl, path) ? 1 : 0;
        }
        if (surfaces == 0) {
            throw new RuntimeException("cronet-keylog-no-builder");
        }
        NetTap.getXposedLogger().log(
                "cronet-keylog: routed %d builder surface(s) to %s for %s",
                surfaces, path, packageName);
        MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_CRONET_KEYLOG);
    }

    private static boolean hookBuilderSetExperimentalOptions(Class<?> builder, String path) {
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
                        RuntimeCaptureConfig.refreshFromXSharedPreferencesIfStale();
                        if (!RuntimeCaptureConfig.isCronetQuicKeylogEnabled()) {
                            return;
                        }
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
     * Some apps skip {@code setExperimentalOptions} entirely. Hook canonical
     * {@code CronetEngineBuilderImpl} build methods and inject the option via
     * the private {@code experimentalOptions} field before the native builder
     * hands off to BoringSSL.
     */
    private static boolean hookBuilderImplBuild(Class<?> impl, String path) {
        if (impl == null) {
            return false;
        }
        int hooked = 0;
        for (Method m : impl.getDeclaredMethods()) {
            if (!isBuilderBuildCandidate(impl, m)) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        RuntimeCaptureConfig.refreshFromXSharedPreferencesIfStale();
                        if (!RuntimeCaptureConfig.isCronetQuicKeylogEnabled()) {
                            return;
                        }
                        try {
                            String merged = mergeKeyLogPath(
                                    readExperimentalOptions(param.thisObject), path);
                            Throwable writeErr = writeExperimentalOptions(param.thisObject, merged);
                            if (writeErr != null) {
                                NetTap.getXposedLogger().logSafe(
                                        "cronet-keylog neither m/experimentalOptions "
                                                + "field settable: %s", writeErr);
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
                        "cronet-keylog CronetEngineBuilderImpl build hook failed: %s", e);
            }
        }
        return hooked > 0;
    }

    static boolean isBuilderBuildCandidate(Class<?> builderClass, Method method) {
        if (builderClass == null || method == null || method.getParameterTypes().length != 0) {
            return false;
        }
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)
                || Modifier.isNative(modifiers)) {
            return false;
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE || returnType == builderClass) {
            return false;
        }
        if ("build".equals(method.getName())) {
            return true;
        }
        String returnName = returnType.getName();
        if (returnName.contains("Cronet") && returnName.contains("Context")) {
            return true;
        }
        return isShortObfuscatedName(method.getName())
                && !returnName.startsWith("java.")
                && !returnName.startsWith("android.");
    }

    private static boolean isShortObfuscatedName(String name) {
        if (name == null || name.isEmpty() || name.length() > 8) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
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

    private static final String[] EXPERIMENTAL_OPTIONS_FIELDS =
            { "mExperimentalOptions", "experimentalOptions" };

    private static String readExperimentalOptions(Object target) {
        for (String field : EXPERIMENTAL_OPTIONS_FIELDS) {
            try {
                Object value = XposedHelpers.getObjectField(target, field);
                if (value != null) {
                    return value.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Throwable writeExperimentalOptions(Object target, String merged) {
        Throwable last = null;
        for (String field : EXPERIMENTAL_OPTIONS_FIELDS) {
            try {
                XposedHelpers.setObjectField(target, field, merged);
                return null;
            } catch (Throwable e) {
                last = e;
            }
        }
        return last;
    }
}
