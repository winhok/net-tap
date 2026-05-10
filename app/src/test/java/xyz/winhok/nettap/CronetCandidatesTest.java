package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class CronetCandidatesTest {
    private static final String OFFICIAL_CRONET = "org.chromium.net.impl.CronetUrlRequest";
    private static final String TTNET_CRONET = "com.ttnet.org.chromium.net.impl.CronetUrlRequest";

    @Test
    public void resolveAllReturnsEmptyForNullClassLoader() {
        List<Class<?>> result = CronetCandidates.resolveAll(null);
        assertTrue("expected empty list for null ClassLoader", result.isEmpty());
    }

    @Test
    public void resolveAllFindsOfficialCronetWhenOnlyEmptyPrefixResolves() {
        ClassLoader cl = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (OFFICIAL_CRONET.equals(name)) {
                    return CronetCandidatesTest.class;
                }
                throw new ClassNotFoundException(name);
            }
        };

        List<Class<?>> result = CronetCandidates.resolveAll(cl);

        assertEquals(1, result.size());
        assertSame(CronetCandidatesTest.class, result.get(0));
    }

    @Test
    public void resolveAllFindsTtnetCronetWhenOnlyTtnetPrefixResolves() {
        ClassLoader cl = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (TTNET_CRONET.equals(name)) {
                    return CronetCandidatesTest.class;
                }
                throw new ClassNotFoundException(name);
            }
        };

        List<Class<?>> result = CronetCandidates.resolveAll(cl);

        assertEquals(1, result.size());
        assertSame(CronetCandidatesTest.class, result.get(0));
    }

    @Test
    public void resolveAllReturnsBothInPrefixOrderWhenBothResolve() {
        ClassLoader cl = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (OFFICIAL_CRONET.equals(name)) {
                    return CronetCandidatesTest.class;
                }
                if (TTNET_CRONET.equals(name)) {
                    return CronetCandidates.class;
                }
                throw new ClassNotFoundException(name);
            }
        };

        List<Class<?>> result = CronetCandidates.resolveAll(cl);

        assertEquals(2, result.size());
        assertSame(
                "first element should match the empty-prefix resolution",
                CronetCandidatesTest.class,
                result.get(0)
        );
        assertSame(
                "second element should match the com.ttnet. prefix resolution",
                CronetCandidates.class,
                result.get(1)
        );
    }

    @Test
    public void resolveAllReturnsEmptyWhenNoPrefixResolves() {
        ClassLoader cl = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };

        List<Class<?>> result = CronetCandidates.resolveAll(cl);

        assertTrue("no candidate should resolve", result.isEmpty());
    }
}
