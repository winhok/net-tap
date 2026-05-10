package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import okio.Buffer;
import okio.BufferedSource;

public final class ReflectTest {

    @Test
    public void findMethod_exactSignatureHit() throws Exception {
        Method m = Reflect.findMethod(String.class, "indexOf", int.class);

        assertNotNull(m);
        assertEquals("indexOf", m.getName());
        assertEquals(int.class, m.getReturnType());
        assertEquals(int.class, m.getParameterTypes()[0]);
        assertEquals(1, ((Integer) m.invoke("abcabc", (int) 'b')).intValue());
    }

    @Test
    public void findMethod_missReturnsNull() {
        assertNull(Reflect.findMethod(String.class, "noSuchMethodXyz"));
        assertNull(Reflect.findMethod(String.class, "indexOf", double.class));
    }

    @Test
    public void findMethod_walksParentChainForPrivateMethod() throws Exception {
        Method m = Reflect.findMethod(HiddenChild.class, "secret");

        assertNotNull(m);
        assertEquals("secret", m.getName());
        assertEquals(int.class, m.getReturnType());
        assertEquals(HiddenParent.class, m.getDeclaringClass());
        assertEquals(7, ((Integer) m.invoke(new HiddenChild())).intValue());
    }

    @Test
    public void findMethodBySignature_byReturnAndParams() {
        Method m = Reflect.findMethodBySignature(String.class, char.class, int.class);

        assertNotNull(m);
        assertEquals("charAt", m.getName());
        assertEquals(char.class, m.getReturnType());
        assertEquals(int.class, m.getParameterTypes()[0]);
    }

    @Test
    public void findMethodBySignature_returnTypeNullMatchesAnyReturn() {
        Method m = Reflect.findMethodBySignature(String.class, null, int.class);

        assertNotNull(m);
        assertEquals(1, m.getParameterTypes().length);
        assertEquals(int.class, m.getParameterTypes()[0]);
    }

    @Test
    public void findMethodBySignature_longLongInterchangeOnReturn() {
        Method m = Reflect.findMethodBySignature(LongHolder.class, Long.class);

        assertNotNull(m);
        assertEquals("size", m.getName());
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void findMethodBySignature_longLongInterchangeOnParam() {
        Method m = Reflect.findMethodBySignature(LongHolder.class, long.class, Long.class);

        assertNotNull(m);
        assertEquals("doubled", m.getName());
        assertEquals(long.class, m.getReturnType());
        assertEquals(long.class, m.getParameterTypes()[0]);
    }

    @Test
    public void findMethodBySignature_intIntegerInterchange() {
        Method m = Reflect.findMethodBySignature(LongHolder.class, Integer.class, Integer.class);

        assertNotNull(m);
        assertEquals("countWords", m.getName());
        assertEquals(int.class, m.getReturnType());
        assertEquals(int.class, m.getParameterTypes()[0]);
    }

    @Test
    public void findUnaryMethodAccepting_nameAndArgMatch() {
        Method m = Reflect.findUnaryMethodAccepting(StringBuilder.class, "append", String.class);

        assertNotNull(m);
        assertEquals("append", m.getName());
        assertEquals(1, m.getParameterTypes().length);
        assertTrue(m.getParameterTypes()[0].isAssignableFrom(String.class));
    }

    @Test
    public void findUnaryMethodAccepting_nullNameMatchesByArgOnly() {
        Method m = Reflect.findUnaryMethodAccepting(UnaryIface.class, null, java.nio.charset.Charset.class);

        assertNotNull(m);
        assertEquals("absorb", m.getName());
        assertEquals(1, m.getParameterTypes().length);
        assertTrue(m.getParameterTypes()[0].isAssignableFrom(java.nio.charset.Charset.class));
    }

    @Test
    public void findReadIntoSinkMethod_okioBufferedSource() {
        Method m = Reflect.findReadIntoSinkMethod(BufferedSource.class, Buffer.class);

        assertNotNull(m);
        Class<?>[] params = m.getParameterTypes();
        assertEquals(2, params.length);
        assertTrue(params[0].isAssignableFrom(Buffer.class));
        assertEquals(long.class, params[1]);
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void findFieldByType_findsMatchingType() {
        Field f = Reflect.findFieldByType(FieldHolder.class, String.class);

        assertNotNull(f);
        assertEquals("name", f.getName());
        assertEquals(String.class, f.getType());
    }

    @Test
    public void findFieldByType_walksParentChain() {
        Field f = Reflect.findFieldByType(FieldChild.class, String.class);

        assertNotNull(f);
        assertEquals("name", f.getName());
        assertEquals(FieldHolder.class, f.getDeclaringClass());
    }

    @Test
    public void findFieldByTypeName_matchesByTypeName() {
        Field f = Reflect.findFieldByTypeName(FieldHolder.class, "java.lang.String");

        assertNotNull(f);
        assertEquals("name", f.getName());
        assertEquals(String.class, f.getType());
    }

    @Test
    public void findFieldByTypeName_missReturnsNull() {
        assertNull(Reflect.findFieldByTypeName(FieldHolder.class, "no.such.Type"));
    }

    @Test
    public void invokeNoArg_hitsMethodAndReturnsValue() throws Exception {
        Object result = Reflect.invokeNoArg(new TestTarget(), "describe");

        assertEquals("describe:default", result);
    }

    @Test(expected = ReflectiveOperationException.class)
    public void invokeNoArg_nullTargetThrowsReflectiveOpException() throws Exception {
        Reflect.invokeNoArg(null, "describe");
    }

    @Test(expected = NoSuchMethodException.class)
    public void invokeNoArg_missingMethodThrowsNoSuchMethod() throws Exception {
        Reflect.invokeNoArg(new TestTarget(), "noSuchMethodXyz");
    }

    @Test
    public void invoke_unwrapsRuntimeExceptionPreservingIdentity() throws Exception {
        Method m = TestTarget.class.getMethod("boom");
        try {
            Reflect.invoke(new TestTarget(), m);
            fail("expected IllegalStateException");
        } catch (IllegalStateException ise) {
            assertEquals("kaboom", ise.getMessage());
            assertNull("RuntimeException cause must not be re-wrapped", ise.getCause());
        }
    }

    @Test
    public void invoke_wrapsCheckedExceptionInRuntimeException() throws Exception {
        Method m = TestTarget.class.getMethod("boomChecked");
        try {
            Reflect.invoke(new TestTarget(), m);
            fail("expected RuntimeException");
        } catch (RuntimeException re) {
            assertEquals("checked exception must be wrapped in plain RuntimeException",
                    RuntimeException.class, re.getClass());
            assertTrue(re.getCause() instanceof IOException);
            assertEquals("io kaboom", re.getCause().getMessage());
        }
    }

    @Test
    public void invokeNoArg_unwrapsRuntimeExceptionFromTarget() throws Exception {
        try {
            Reflect.invokeNoArg(new TestTarget(), "boom");
            fail("expected IllegalStateException");
        } catch (IllegalStateException ise) {
            assertEquals("kaboom", ise.getMessage());
            assertNull("RuntimeException cause must not be re-wrapped", ise.getCause());
        }
    }

    @Test
    public void findReadIntoSinkMethod_withCustomPreferredNames_findsObfuscatedAlias() {
        Method m = Reflect.findReadIntoSinkMethod(ObfuscatedReader.class, Buffer.class, "v0");

        assertNotNull(m);
        assertEquals("v0", m.getName());
        Class<?>[] params = m.getParameterTypes();
        assertEquals(2, params.length);
        assertTrue(params[0].isAssignableFrom(Buffer.class));
        assertEquals(long.class, params[1]);
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void newInstance_constructsInstance() {
        Object instance = Reflect.newInstance(java.util.ArrayList.class);

        assertNotNull(instance);
        assertTrue(instance instanceof java.util.ArrayList);
        assertTrue(((java.util.ArrayList<?>) instance).isEmpty());
    }

    @Test
    public void newInstance_unwrapsRuntimeException() {
        try {
            Reflect.newInstance(ThrowsRuntimeInCtor.class);
            fail("expected IllegalStateException");
        } catch (IllegalStateException ise) {
            assertEquals("ctor kaboom", ise.getMessage());
            assertNull("RuntimeException cause must not be re-wrapped", ise.getCause());
        }
    }

    @Test
    public void newInstance_wrapsCheckedExceptionInRuntimeException() {
        try {
            Reflect.newInstance(ThrowsCheckedInCtor.class);
            fail("expected RuntimeException");
        } catch (RuntimeException re) {
            assertEquals("checked exception must be wrapped in plain RuntimeException",
                    RuntimeException.class, re.getClass());
            assertTrue(re.getCause() instanceof IOException);
            assertEquals("io ctor kaboom", re.getCause().getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstance_throwsIaeOnNullClass() {
        Reflect.newInstance(null);
    }

    public static class ObfuscatedReader {
        @SuppressWarnings("unused")
        public long v0(Buffer sink, long byteCount) {
            return byteCount;
        }
    }

    public static class ThrowsRuntimeInCtor {
        public ThrowsRuntimeInCtor() {
            throw new IllegalStateException("ctor kaboom");
        }
    }

    public static class ThrowsCheckedInCtor {
        public ThrowsCheckedInCtor() throws IOException {
            throw new IOException("io ctor kaboom");
        }
    }

    public static class HiddenParent {
        @SuppressWarnings("unused")
        private int secret() {
            return 7;
        }
    }

    public static class HiddenChild extends HiddenParent {
    }

    public interface UnaryIface {
        void absorb(java.nio.charset.Charset cs);
    }

    public static class LongHolder {
        public long size() {
            return 42L;
        }

        public long doubled(long x) {
            return x * 2L;
        }

        public int countWords(int totalChars) {
            return totalChars / 5;
        }
    }

    public static class FieldHolder {
        @SuppressWarnings("unused")
        private String name = "n";
        @SuppressWarnings("unused")
        private int count = 1;
        @SuppressWarnings("unused")
        private long size = 2L;
    }

    public static class FieldChild extends FieldHolder {
    }

    public static class TestTarget {
        public String describe() {
            return "describe:default";
        }

        public void boom() {
            throw new IllegalStateException("kaboom");
        }

        public void boomChecked() throws IOException {
            throw new IOException("io kaboom");
        }
    }
}
