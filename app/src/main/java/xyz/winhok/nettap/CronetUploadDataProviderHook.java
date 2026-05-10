package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final ConcurrentHashMap<Class<?>, Field> BUFFER_FIELD_CACHE = new ConcurrentHashMap<>();

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
        return INSTALL_REGISTRY.installOnce(
                classLoader,
                candidates,
                null,
                packageName,
                (cls, collector) -> {
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllConstructors(
                            cls, new CtorHook()));
                    CronetInstallRegistry.addAll(collector, XposedBridge.hookAllMethods(
                            cls, "onReadSucceeded", new OnReadSucceededHook()));
                });
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
            Field field = BUFFER_FIELD_CACHE.get(cls);
            if (field == null) {
                field = Reflect.findFieldByType(cls, ByteBuffer.class);
                if (field == null) {
                    return null;
                }
                BUFFER_FIELD_CACHE.putIfAbsent(cls, field);
            }
            try {
                Object v = field.get(uploadStream);
                return v instanceof ByteBuffer ? (ByteBuffer) v : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
