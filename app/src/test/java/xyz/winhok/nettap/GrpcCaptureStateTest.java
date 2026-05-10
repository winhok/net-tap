package xyz.winhok.nettap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the pure-Java parts of the gRPC hook layer.
 * {@link GrpcCallInstaller} and {@link GrpcListenerProxy} transitively link
 * the Xposed API which is {@code compileOnly} and therefore not on the unit
 * test classpath (see {@link CronetUrlRequestHookTest} for the same pattern),
 * so those classes are covered only indirectly here.
 */
public final class GrpcCaptureStateTest {

    @Test
    public void idIsUnique() {
        GrpcCaptureState a = new GrpcCaptureState("com.foo");
        GrpcCaptureState b = new GrpcCaptureState("com.foo");
        assertNotNull(a.id);
        assertNotNull(b.id);
        assertFalse("ids must differ", a.id.equals(b.id));
    }

    @Test
    public void defaultsAreEmpty() {
        GrpcCaptureState s = new GrpcCaptureState("com.foo");
        assertTrue(s.requestHeaders.isEmpty());
        assertTrue(s.responseHeaders.isEmpty());
        assertNull(s.requestBodyFirstMessage);
        assertNull(s.responseBodyFirstMessage);
    }

    @Test
    public void mutexIsNotNull() {
        assertNotNull(new GrpcCaptureState("com.foo").mutex);
    }
}
