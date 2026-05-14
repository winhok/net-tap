package xyz.winhok.nettap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chromium.net.impl.CronetUploadDataStream;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;

public final class CronetUploadDataProviderHookCoverageTest {
    @Test
    public void uploadHookIncludesStructuralNoArgReadCallbacks() {
        Set<Method> candidates = CronetUploadDataProviderHook.readSucceededCandidates(
                CronetUploadDataStream.class);

        assertTrue(contains(candidates, "onReadSucceeded"));
        assertTrue(contains(candidates, "LIZ"));
        assertFalse(contains(candidates, "LIZIZ"));
    }

    private static boolean contains(Set<Method> methods, String methodName) {
        for (Method method : methods) {
            if (methodName.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }
}
