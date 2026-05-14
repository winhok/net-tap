package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Writes TLS 1.2/1.3 secrets to an NSS-format SSL key log file so Wireshark
 * can decrypt captured traffic offline. No packet capture is performed — the
 * operator is expected to record traffic with a VPN mirror or carrier-side
 * tap and marry the pcap with this key log.
 *
 * <p>Surfaces tried, in order:
 * <ul>
 *   <li>{@code NativeCrypto.SSL_do_handshake} + {@code SSL_get_key_log_line}
 *       on newer Conscrypt builds — returns a pre-formatted NSS line.</li>
 *   <li>{@code ConscryptEngineSocket.startHandshake()} reading the
 *       {@code NativeSsl} fields directly — works on older Conscrypt.</li>
 *   <li>{@code javax.net.ssl.keyLogFile} system property — covers any JSSE
 *       provider that honors the Oracle convention.</li>
 * </ul>
 *
 * <p>If no surface is reachable the installer logs and returns false without
 * throwing so the rest of the capture pipeline stays functional.
 */
public final class TlsKeyLogHook {

    private static final FileLogger KEYLOG = new FileLogger(
            CaptureConfig.TLS_KEYLOG_FILENAME, true);
    private static final AtomicBoolean HEADER_WRITTEN = new AtomicBoolean(false);

    private TlsKeyLogHook() {
    }

    public static boolean install(String packageName, ClassLoader classLoader) {
        if (classLoader == null || !RuntimeCaptureConfig.isTlsKeylogEnabled()) {
            return false;
        }
        try {
            return InstallGuard.installOncePerLoader("tls-keylog", classLoader,
                    () -> installInner(packageName, classLoader));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("tls-keylog install wrapper failed: %s", e);
            return false;
        }
    }

    private static void installInner(String packageName, ClassLoader classLoader) {
        int installedSurfaces = 0;
        installedSurfaces += installConscryptNativeCrypto(classLoader) ? 1 : 0;
        installedSurfaces += installConscryptSocket(classLoader) ? 1 : 0;
        // JSSE property sink is advisory — many providers ignore it. Set it
        // opportunistically but do not count it as an "installed surface" so
        // operators get an honest signal about Conscrypt coverage.
        publishJsseProperty();
        if (installedSurfaces == 0) {
            throw new RuntimeException("tls-keylog-no-surface");
        }
        NetTap.getXposedLogger().log(
                "tls-keylog: installed %d surface(s) for %s", installedSurfaces, packageName);
        MetricsReporter.incInstalled(packageName, MetricsReporter.LAYER_TLS_KEYLOG);
    }

    /**
     * Newer Conscrypt exposes {@code NativeCrypto.SSL_get_key_log_line(ssl)}
     * that returns a pre-formatted NSS line. Hook
     * {@code NativeCrypto.SSL_do_handshake} and, on success, read the line
     * directly.
     */
    private static boolean installConscryptNativeCrypto(ClassLoader classLoader) {
        Class<?> nativeCrypto = XposedHelpers.findClassIfExists(
                "org.conscrypt.NativeCrypto", classLoader);
        if (nativeCrypto == null) {
            return false;
        }
        Method getKeyLogLine = null;
        for (Method m : nativeCrypto.getDeclaredMethods()) {
            if ("SSL_get_key_log_line".equals(m.getName())) {
                getKeyLogLine = m;
                break;
            }
        }
        if (getKeyLogLine == null) {
            return false;
        }
        final Method keyLogLineRef = getKeyLogLine;
        int hooked = 0;
        for (Method m : nativeCrypto.getDeclaredMethods()) {
            if (!"SSL_do_handshake".equals(m.getName())) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getThrowable() != null || param.args == null
                                || param.args.length == 0) {
                            return;
                        }
                        try {
                            Object sslRef = param.args[0];
                            keyLogLineRef.setAccessible(true);
                            Object line = keyLogLineRef.invoke(null, sslRef);
                            if (line instanceof String && !((String) line).isEmpty()) {
                                writeTlsLine((String) line);
                            }
                        } catch (Throwable e) {
                            NetTap.getXposedLogger().logSafe(
                                    "tls-keylog nativeCrypto dump failed: %s", e);
                        }
                    }
                });
                hooked++;
            } catch (Throwable e) {
                NetTap.getXposedLogger().logSafe(
                        "tls-keylog SSL_do_handshake hook failed: %s", e);
            }
        }
        return hooked > 0;
    }

    /**
     * Older Conscrypt builds require reading session secrets out of the
     * {@code NativeSsl} field by hand. We hook
     * {@code ConscryptEngineSocket.startHandshake()}; by the time it returns,
     * the session is established and fields are populated.
     */
    private static boolean installConscryptSocket(ClassLoader classLoader) {
        Class<?> socket = XposedHelpers.findClassIfExists(
                "org.conscrypt.ConscryptEngineSocket", classLoader);
        if (socket == null) {
            return false;
        }
        int hooked = 0;
        for (Method m : socket.getDeclaredMethods()) {
            if (!"startHandshake".equals(m.getName()) || m.getParameterTypes().length != 0) {
                continue;
            }
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getThrowable() != null) {
                            return;
                        }
                        try {
                            dumpConscryptSecrets(param.thisObject);
                        } catch (Throwable e) {
                            NetTap.getXposedLogger().logSafe(
                                    "tls-keylog conscrypt socket dump failed: %s", e);
                        }
                    }
                });
                hooked++;
            } catch (Throwable e) {
                NetTap.getXposedLogger().logSafe(
                        "tls-keylog ConscryptEngineSocket.startHandshake hook failed: %s", e);
            }
        }
        return hooked > 0;
    }

    /**
     * Set {@code javax.net.ssl.keyLogFile} so any JSSE provider honoring the
     * Oracle convention writes NSS lines directly to our path. Does not
     * require a hook — it's a no-op on Conscrypt but helps on OEM forks and
     * embedded JSSE consumers. Advisory only; callers do not treat this as
     * an installed surface.
     */
    private static void publishJsseProperty() {
        try {
            String path = resolveKeyLogPath();
            if (path == null) {
                return;
            }
            System.setProperty("javax.net.ssl.keyLogFile", path);
        } catch (Throwable e) {
            NetTap.getXposedLogger().logSafe("tls-keylog jsse property set failed: %s", e);
        }
    }

    static String resolveKeyLogPath() {
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

    private static void dumpConscryptSecrets(Object engineOrSocket) throws Throwable {
        if (engineOrSocket == null) {
            return;
        }
        Object nativeSsl = findByTypeName(engineOrSocket, "org.conscrypt.NativeSsl");
        if (nativeSsl == null) {
            return;
        }
        Method keyLogLine = findNoArgMethod(nativeSsl.getClass(), "keyLogLine");
        if (keyLogLine != null) {
            Object out = keyLogLine.invoke(nativeSsl);
            if (out instanceof String && !((String) out).isEmpty()) {
                writeTlsLine((String) out);
                return;
            }
        }
        byte[] clientRandom = readByteField(nativeSsl, "clientRandom");
        byte[] masterSecret = readByteField(nativeSsl, "masterSecret");
        if (clientRandom != null && masterSecret != null) {
            writeTlsLine("CLIENT_RANDOM " + hex(clientRandom) + " " + hex(masterSecret));
        }
        emitIfPresent(nativeSsl, "clientHandshakeTrafficSecret",
                "CLIENT_HANDSHAKE_TRAFFIC_SECRET", clientRandom);
        emitIfPresent(nativeSsl, "serverHandshakeTrafficSecret",
                "SERVER_HANDSHAKE_TRAFFIC_SECRET", clientRandom);
        emitIfPresent(nativeSsl, "clientApplicationTrafficSecret",
                "CLIENT_TRAFFIC_SECRET_0", clientRandom);
        emitIfPresent(nativeSsl, "serverApplicationTrafficSecret",
                "SERVER_TRAFFIC_SECRET_0", clientRandom);
        emitIfPresent(nativeSsl, "exporterMasterSecret",
                "EXPORTER_SECRET", clientRandom);
    }

    private static void emitIfPresent(Object nativeSsl, String fieldName, String nssLabel,
                                      byte[] clientRandom) {
        if (clientRandom == null) {
            return;
        }
        byte[] secret = readByteField(nativeSsl, fieldName);
        if (secret == null) {
            return;
        }
        writeTlsLine(nssLabel + " " + hex(clientRandom) + " " + hex(secret));
    }

    private static byte[] readByteField(Object target, String name) {
        try {
            Field f = findField(target.getClass(), name);
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            Object v = f.get(target);
            return v instanceof byte[] ? (byte[]) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findNoArgMethod(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                if (name.equals(f.getName())) {
                    return f;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object findByTypeName(Object root, String typeName) {
        Class<?> c = root.getClass();
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                if (typeName.equals(f.getType().getName())) {
                    try {
                        f.setAccessible(true);
                        return f.get(root);
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String hex(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[i * 2] = HEX[(data[i] >>> 4) & 0x0F];
            out[i * 2 + 1] = HEX[data[i] & 0x0F];
        }
        return new String(out);
    }

    /**
     * Append a single NSS key log line. Package-private so
     * {@link CronetKeyLogHook} can route BoringSSL callbacks through the same
     * file and benefit from the async writer.
     */
    static void writeLine(String nssLine) {
        if (nssLine == null || nssLine.isEmpty()) {
            return;
        }
        // Header must land before any key line. The writer thread is single
        // threaded and the queue is FIFO, so enqueuing both under the same
        // monitor gives us ordered delivery on disk. Without the lock a racing
        // caller could enqueue a key line between our CAS and logRawLine.
        if (!HEADER_WRITTEN.get()) {
            synchronized (KEYLOG) {
                if (HEADER_WRITTEN.compareAndSet(false, true)) {
                    KEYLOG.logRawLine("# SSL/TLS secrets log file, see "
                            + "https://developer.mozilla.org/docs/Mozilla/Projects/NSS/Key_Log_Format");
                }
            }
        }
        KEYLOG.logRawLine(nssLine);
    }

    private static void writeTlsLine(String nssLine) {
        RuntimeCaptureConfig.refreshFromXSharedPreferencesIfStale();
        if (!RuntimeCaptureConfig.isTlsKeylogEnabled()) {
            return;
        }
        writeLine(nssLine);
    }
}
