package xyz.winhok.nettap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Captures Cronet request body by hooking
 * {@code org.chromium.net.impl.CronetUploadDataStream.onReadSucceeded} (and
 * shaded variants). The upload stream reads from the app's UploadDataProvider
 * into an internal ByteBuffer; each {@code onReadSucceeded} call reports how
 * many bytes were produced, which we forward to
 * {@link CronetUrlRequestHook#attachRequestBodyChunk} keyed by the owning
 * CronetUrlRequest.
 */
public final class CronetUploadDataProviderHook {

    private static final CronetInstallRegistry INSTALL_REGISTRY = new CronetInstallRegistry();
    private static final Map<Object, Object> STREAM_TO_REQUEST =
            Collections.synchronizedMap(new WeakHashMap<Object, Object>());

    private CronetUploadDataProviderHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        List<Class<?>> candidates = CronetCandidates.resolveAllWithSuffix(
                classLoader, CronetCandidates.CRONET_UPLOAD_DATA_STREAM_SUFFIX);
        if (candidates.isEmpty()) {
            return false;
        }

        int hooked = 0;
        for (Class<?> cls : candidates) {
            String className = cls.getName();
            synchronized (INSTALL_REGISTRY) {
                if (INSTALL_REGISTRY.isInstalled(classLoader, className)) {
                    hooked++;
                    continue;
                }
                List<XC_MethodHook.Unhook> installedHooks = new ArrayList<>();
                try {
                    addAll(installedHooks, XposedBridge.hookAllConstructors(
                            cls, new CtorHook()));
                    addAll(installedHooks, XposedBridge.hookAllMethods(
                            cls, "onReadSucceeded", new OnReadSucceededHook()));
                    INSTALL_REGISTRY.markInstalled(classLoader, className);
                    NetTap.getXposedLogger().log(
                            "installed hook: %s", className);
                    hooked++;
                } catch (Throwable e) {
                    for (int i = installedHooks.size() - 1; i >= 0; i--) {
                        try {
                            installedHooks.get(i).unhook();
                        } catch (Throwable ignored) {
                        }
                    }
                    INSTALL_REGISTRY.unmarkInstalled(classLoader, className);
                    NetTap.getXposedLogger().log(
                            "failed to install %s hook: %s", className, e);
                }
            }
        }
        return hooked > 0;
    }

    private static void addAll(List<XC_MethodHook.Unhook> dst, java.util.Set<XC_MethodHook.Unhook> src) {
        if (src != null) {
            dst.addAll(src);
        }
    }

    private static final class CtorHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                if (param.args == null) {
                    return;
                }
                Object owner = null;
                for (Object a : param.args) {
                    if (a == null) {
                        continue;
                    }
                    String name = a.getClass().getName();
                    if (name.endsWith("CronetUrlRequest")) {
                        owner = a;
                        break;
                    }
                }
                if (owner != null) {
                    STREAM_TO_REQUEST.put(param.thisObject, owner);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class OnReadSucceededHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                Object owner = STREAM_TO_REQUEST.get(param.thisObject);
                if (owner == null || param.args == null) {
                    return;
                }
                ByteBuffer buffer = extractBuffer(param.thisObject);
                if (buffer == null) {
                    return;
                }
                int bytesRead = buffer.position();
                if (bytesRead <= 0) {
                    return;
                }
                CronetUrlRequestHook.attachRequestBodyChunk(
                        owner, buffer, bytesRead, 0, buffer.limit());
            } catch (Throwable ignored) {
            }
        }

        private static ByteBuffer extractBuffer(Object uploadStream) {
            if (uploadStream == null) {
                return null;
            }
            Class<?> cls = uploadStream.getClass();
            while (cls != null) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    if (ByteBuffer.class.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(uploadStream);
                            if (v instanceof ByteBuffer) {
                                return (ByteBuffer) v;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
            return null;
        }
    }
}
