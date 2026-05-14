package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class CronetKeyLogHookCoverageTest {
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
    public void installReturnsFalseWhenDisabledOrClassLoaderMissing() {
        RuntimeCaptureConfig.applyOverrides(
                true,
                RuntimeCaptureConfig.DEFAULT_REALTIME_PORT,
                RuntimeCaptureConfig.DEFAULT_REALTIME_QUEUE_CAPACITY,
                RuntimeCaptureConfig.DEFAULT_REALTIME_TIMEOUT_MS,
                true,
                true,
                false
        );

        assertFalse(CronetKeyLogHook.install("pkg", getClass().getClassLoader()));
        assertFalse(CronetKeyLogHook.install("pkg", null));
    }

    @Test
    public void builderSetExperimentalOptionsHookMergesStringArgumentsOnly() throws Exception {
        assertFalse(hookBuilderSetExperimentalOptions(null, "/tmp/key.log"));
        assertTrue(hookBuilderSetExperimentalOptions(BuilderSurface.class, "/tmp/key.log"));
        assertEquals(1, XposedBridge.hookedMethods().size());

        XC_MethodHook hook = XposedBridge.hookedMethods().get(0).callback;
        XC_MethodHook.MethodHookParam nullArgs = param(new BuilderSurface());
        nullArgs.args = null;
        invokeHook(hook, nullArgs);

        XC_MethodHook.MethodHookParam emptyArgs = param(new BuilderSurface());
        emptyArgs.args = new Object[0];
        invokeHook(hook, emptyArgs);

        XC_MethodHook.MethodHookParam nonString = param(new BuilderSurface(), Integer.valueOf(7));
        invokeHook(hook, nonString);
        assertEquals("{\"ssl_key_log_file\":\"/tmp/key.log\"}", nonString.args[0]);

        XC_MethodHook.MethodHookParam stringArg = param(new BuilderSurface(), "{\"QUIC\":{}}");
        invokeHook(hook, stringArg);
        assertEquals(
                "{\"ssl_key_log_file\":\"/tmp/key.log\",\"QUIC\":{}}",
                stringArg.args[0]
        );
    }

    @Test
    public void builderImplBuildHookReadsAndWritesExperimentalOptionsFields() throws Exception {
        assertFalse(hookBuilderImplBuild(null, "/tmp/key.log"));
        assertTrue(hookBuilderImplBuild(BuilderImplSurface.class, "/tmp/key.log"));
        assertEquals(1, XposedBridge.hookedMethods().size());

        BuilderImplSurface target = new BuilderImplSurface();
        target.experimentalOptions = "{\"QUIC\":{}}";

        invokeHook(XposedBridge.hookedMethods().get(0).callback, param(target));

        assertEquals(
                "{\"ssl_key_log_file\":\"/tmp/key.log\",\"QUIC\":{}}",
                target.mExperimentalOptions
        );
    }

    @Test
    public void builderImplBuildHookToleratesUnsettableOptionsFields() throws Exception {
        assertTrue(hookBuilderImplBuild(UnsettableBuilderImplSurface.class, "/tmp/key.log"));

        invokeHook(
                XposedBridge.hookedMethods().get(0).callback,
                param(new UnsettableBuilderImplSurface())
        );
    }

    @Test
    public void readAndWriteExperimentalOptionsFallbackAcrossFieldNames() throws Exception {
        BuilderImplSurface target = new BuilderImplSurface();
        target.experimentalOptions = "{\"old\":true}";

        assertEquals("{\"old\":true}", readExperimentalOptions(target));
        assertNull(writeExperimentalOptions(target, "{\"new\":true}"));
        assertEquals("{\"new\":true}", target.mExperimentalOptions);
        assertTrue(writeExperimentalOptions(new UnsettableBuilderImplSurface(), "{}") != null);
    }

    private static boolean hookBuilderSetExperimentalOptions(Class<?> type, String path) throws Exception {
        Method method = CronetKeyLogHook.class.getDeclaredMethod(
                "hookBuilderSetExperimentalOptions",
                Class.class,
                String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, type, path);
    }

    private static boolean hookBuilderImplBuild(Class<?> type, String path) throws Exception {
        Method method = CronetKeyLogHook.class.getDeclaredMethod(
                "hookBuilderImplBuild",
                Class.class,
                String.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, type, path);
    }

    private static String readExperimentalOptions(Object target) throws Exception {
        Method method = CronetKeyLogHook.class.getDeclaredMethod("readExperimentalOptions", Object.class);
        method.setAccessible(true);
        return (String) method.invoke(null, target);
    }

    private static Throwable writeExperimentalOptions(Object target, String value) throws Exception {
        Method method = CronetKeyLogHook.class.getDeclaredMethod(
                "writeExperimentalOptions",
                Object.class,
                String.class
        );
        method.setAccessible(true);
        return (Throwable) method.invoke(null, target, value);
    }

    private static XC_MethodHook.MethodHookParam param(Object thisObject, Object... args) {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
        param.thisObject = thisObject;
        param.args = args;
        return param;
    }

    private static void invokeHook(XC_MethodHook hook, XC_MethodHook.MethodHookParam param) throws Exception {
        Method method = hook.getClass().getDeclaredMethod(
                "beforeHookedMethod",
                XC_MethodHook.MethodHookParam.class
        );
        method.setAccessible(true);
        method.invoke(hook, param);
    }

    public static final class BuilderSurface {
        public void setExperimentalOptions(String options) {
        }

        public void setExperimentalOptions(int ignored) {
        }
    }

    public static final class BuilderImplSurface {
        public String mExperimentalOptions;
        public String experimentalOptions;

        public Object build() {
            return new Object();
        }

        public Object build(String ignored) {
            return new Object();
        }
    }

    public static final class UnsettableBuilderImplSurface {
        public Object build() {
            return new Object();
        }
    }
}
