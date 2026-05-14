package xyz.winhok.nettap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class TlsKeyLogHookCoverageTest {
    @Before
    public void setUp() {
        InstallGuard.resetForTesting();
        RuntimeCaptureConfig.resetForTests();
    }

    @After
    public void tearDown() throws Exception {
        InstallGuard.resetForTesting();
        RuntimeCaptureConfig.resetForTests();
        resetHeaderWritten();
    }

    @Test
    public void installReturnsFalseWhenDisabledOrClassLoaderMissing() {
        RuntimeCaptureConfig.applyOverrides(
                true,
                RuntimeCaptureConfig.DEFAULT_REALTIME_PORT,
                RuntimeCaptureConfig.DEFAULT_REALTIME_QUEUE_CAPACITY,
                RuntimeCaptureConfig.DEFAULT_REALTIME_TIMEOUT_MS,
                true,
                false,
                true
        );

        assertFalse(TlsKeyLogHook.install("pkg", getClass().getClassLoader()));
        assertFalse(TlsKeyLogHook.install("pkg", null));
    }

    @Test
    public void resolveKeyLogPathReturnsNullWhenNoGlobalAndroidContextExists() {
        assertNull(TlsKeyLogHook.resolveKeyLogPath());
    }

    @Test
    public void readByteFieldFindsInheritedByteArraysOnly() throws Exception {
        DerivedSecrets secrets = new DerivedSecrets();

        assertArrayEquals(new byte[] {0x01, 0x23}, readByteField(secrets, "clientRandom"));
        assertNull(readByteField(secrets, "notBytes"));
        assertNull(readByteField(secrets, "missing"));
    }

    @Test
    public void privateLookupHelpersWalkSuperclassesAndHandleMisses() throws Exception {
        Method method = findNoArgMethod(DerivedSecrets.class, "keyLogLine");
        Field field = findField(DerivedSecrets.class, "clientRandom");

        assertEquals("keyLogLine", method.getName());
        assertEquals("clientRandom", field.getName());
        assertNull(findNoArgMethod(DerivedSecrets.class, "missing"));
        assertNull(findField(DerivedSecrets.class, "missing"));
    }

    @Test
    public void hexFormatsLowercasePairsForSignedBytes() throws Exception {
        assertEquals("000fff80", hex(new byte[] {0x00, 0x0f, (byte) 0xff, (byte) 0x80}));
    }

    @Test
    public void secretDumpHelpersTolerateMissingInputs() throws Exception {
        dumpConscryptSecrets(null);
        emitIfPresent(new DerivedSecrets(), "clientApplicationTrafficSecret",
                "CLIENT_TRAFFIC_SECRET_0", null);
        emitIfPresent(new DerivedSecrets(), "missing",
                "CLIENT_TRAFFIC_SECRET_0", new byte[] {0x01});
        TlsKeyLogHook.writeLine(null);
        TlsKeyLogHook.writeLine("");
        TlsKeyLogHook.writeLine("CLIENT_RANDOM 00 11");
        assertTrue(findByTypeName(new DerivedSecrets(), "org.conscrypt.NativeSsl") == null);
    }

    private static byte[] readByteField(Object target, String name) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod(
                "readByteField",
                Object.class,
                String.class
        );
        method.setAccessible(true);
        return (byte[]) method.invoke(null, target, name);
    }

    private static Method findNoArgMethod(Class<?> type, String name) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod(
                "findNoArgMethod",
                Class.class,
                String.class
        );
        method.setAccessible(true);
        return (Method) method.invoke(null, type, name);
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod("findField", Class.class, String.class);
        method.setAccessible(true);
        return (Field) method.invoke(null, type, name);
    }

    private static String hex(byte[] value) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod("hex", byte[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, new Object[] {value});
    }

    private static void dumpConscryptSecrets(Object value) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod("dumpConscryptSecrets", Object.class);
        method.setAccessible(true);
        method.invoke(null, value);
    }

    private static void emitIfPresent(
            Object nativeSsl,
            String fieldName,
            String label,
            byte[] clientRandom
    ) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod(
                "emitIfPresent",
                Object.class,
                String.class,
                String.class,
                byte[].class
        );
        method.setAccessible(true);
        method.invoke(null, nativeSsl, fieldName, label, clientRandom);
    }

    private static Object findByTypeName(Object root, String typeName) throws Exception {
        Method method = TlsKeyLogHook.class.getDeclaredMethod(
                "findByTypeName",
                Object.class,
                String.class
        );
        method.setAccessible(true);
        return method.invoke(null, root, typeName);
    }

    private static void resetHeaderWritten() throws Exception {
        Field field = TlsKeyLogHook.class.getDeclaredField("HEADER_WRITTEN");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) field.get(null)).set(false);
    }

    private static class BaseSecrets {
        @SuppressWarnings("unused")
        private final byte[] clientRandom = new byte[] {0x01, 0x23};

        @SuppressWarnings("unused")
        private final String notBytes = "nope";

        @SuppressWarnings("unused")
        private String keyLogLine() {
            return "CLIENT_RANDOM 0123 4567";
        }
    }

    private static final class DerivedSecrets extends BaseSecrets {
        @SuppressWarnings("unused")
        private final byte[] clientApplicationTrafficSecret = new byte[] {0x45};
    }
}
