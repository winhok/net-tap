package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class InstallGuardTest {

    private static final String HOOK_A = "hook.A";
    private static final String HOOK_B = "hook.B";

    @Before
    public void setUp() {
        InstallGuard.resetForTesting();
    }

    @After
    public void tearDown() {
        InstallGuard.resetForTesting();
    }

    @Test
    public void firstCallExecutesActionAndReturnsTrue() throws Throwable {
        ClassLoader loader = newLoader();
        AtomicInteger calls = new AtomicInteger();

        boolean result = InstallGuard.installOncePerLoader(HOOK_A, loader, calls::incrementAndGet);

        assertTrue("first call should run action and return true", result);
        assertEquals(1, calls.get());
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader));
    }

    @Test
    public void secondCallSameHookAndLoaderSkipsAction() throws Throwable {
        ClassLoader loader = newLoader();
        AtomicInteger calls = new AtomicInteger();

        boolean first = InstallGuard.installOncePerLoader(HOOK_A, loader, calls::incrementAndGet);
        boolean second = InstallGuard.installOncePerLoader(HOOK_A, loader, calls::incrementAndGet);

        assertTrue(first);
        assertFalse("second call should not run action and must return false", second);
        assertEquals(1, calls.get());
    }

    @Test
    public void differentHookIdsOnSameLoaderAreIndependent() throws Throwable {
        ClassLoader loader = newLoader();
        AtomicInteger callsA = new AtomicInteger();
        AtomicInteger callsB = new AtomicInteger();

        boolean a = InstallGuard.installOncePerLoader(HOOK_A, loader, callsA::incrementAndGet);
        boolean b = InstallGuard.installOncePerLoader(HOOK_B, loader, callsB::incrementAndGet);

        assertTrue(a);
        assertTrue(b);
        assertEquals(1, callsA.get());
        assertEquals(1, callsB.get());
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader));
        assertTrue(InstallGuard.isInstalled(HOOK_B, loader));
    }

    @Test
    public void sameHookIdOnDifferentLoadersAreIndependent() throws Throwable {
        ClassLoader loader1 = newLoader();
        ClassLoader loader2 = newLoader();
        assertTrue("test prerequisite: distinct loaders", loader1 != loader2);

        AtomicInteger calls = new AtomicInteger();

        boolean r1 = InstallGuard.installOncePerLoader(HOOK_A, loader1, calls::incrementAndGet);
        boolean r2 = InstallGuard.installOncePerLoader(HOOK_A, loader2, calls::incrementAndGet);

        assertTrue(r1);
        assertTrue(r2);
        assertEquals(2, calls.get());
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader1));
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader2));
    }

    @Test
    public void nullLoaderReturnsFalseAndDoesNotRunAction() throws Throwable {
        AtomicInteger calls = new AtomicInteger();

        boolean result = InstallGuard.installOncePerLoader(HOOK_A, null, calls::incrementAndGet);

        assertFalse(result);
        assertEquals(0, calls.get());
    }

    @Test
    public void nullHookIdThrowsIllegalArgumentException() {
        ClassLoader loader = newLoader();
        try {
            InstallGuard.installOncePerLoader(null, loader, () -> { });
            fail("expected IllegalArgumentException for null hookId");
        } catch (IllegalArgumentException expected) {
            // ok
        } catch (Throwable t) {
            fail("expected IllegalArgumentException but got " + t);
        }
    }

    @Test
    public void emptyHookIdThrowsIllegalArgumentException() {
        ClassLoader loader = newLoader();
        try {
            InstallGuard.installOncePerLoader("", loader, () -> { });
            fail("expected IllegalArgumentException for empty hookId");
        } catch (IllegalArgumentException expected) {
            // ok
        } catch (Throwable t) {
            fail("expected IllegalArgumentException but got " + t);
        }
    }

    @Test
    public void runtimeExceptionFromActionPropagatesAndRollsBackFlag() {
        ClassLoader loader = newLoader();
        AtomicInteger callsFail = new AtomicInteger();
        AtomicInteger callsRetry = new AtomicInteger();

        RuntimeException raised = null;
        try {
            InstallGuard.installOncePerLoader(HOOK_A, loader, () -> {
                callsFail.incrementAndGet();
                throw new RuntimeException("boom");
            });
            fail("expected RuntimeException to propagate");
        } catch (RuntimeException re) {
            raised = re;
        } catch (Throwable t) {
            fail("expected RuntimeException but got " + t);
        }
        assertNotNull(raised);
        assertEquals("boom", raised.getMessage());
        assertEquals(1, callsFail.get());
        assertFalse("flag must be rolled back after failure", InstallGuard.isInstalled(HOOK_A, loader));

        boolean retry;
        try {
            retry = InstallGuard.installOncePerLoader(HOOK_A, loader, callsRetry::incrementAndGet);
        } catch (Throwable t) {
            throw new AssertionError("retry should succeed but threw " + t, t);
        }
        assertTrue("retry should run action again after rollback", retry);
        assertEquals(1, callsRetry.get());
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader));
    }

    @Test
    public void checkedThrowableFromActionPropagatesAndRollsBackFlag() {
        ClassLoader loader = newLoader();
        AtomicInteger callsFail = new AtomicInteger();
        AtomicInteger callsRetry = new AtomicInteger();

        Throwable raised = null;
        try {
            InstallGuard.installOncePerLoader(HOOK_A, loader, () -> {
                callsFail.incrementAndGet();
                throw new Throwable("checked-throwable");
            });
            fail("expected Throwable to propagate");
        } catch (Throwable t) {
            raised = t;
        }
        assertNotNull(raised);
        assertEquals("checked-throwable", raised.getMessage());
        assertEquals(1, callsFail.get());
        assertFalse("flag must be rolled back after Throwable", InstallGuard.isInstalled(HOOK_A, loader));

        boolean retry;
        try {
            retry = InstallGuard.installOncePerLoader(HOOK_A, loader, callsRetry::incrementAndGet);
        } catch (Throwable t) {
            throw new AssertionError("retry should succeed but threw " + t, t);
        }
        assertTrue(retry);
        assertEquals(1, callsRetry.get());
    }

    @Test
    public void isInstalledReflectsLifecycle() throws Throwable {
        ClassLoader loader = newLoader();
        assertFalse("not installed before any call", InstallGuard.isInstalled(HOOK_A, loader));

        AtomicInteger calls = new AtomicInteger();
        boolean ran = InstallGuard.installOncePerLoader(HOOK_A, loader, calls::incrementAndGet);
        assertTrue(ran);
        assertTrue("installed after a successful action", InstallGuard.isInstalled(HOOK_A, loader));

        boolean second = InstallGuard.installOncePerLoader(HOOK_A, loader, () -> {
            throw new RuntimeException("must not run");
        });
        assertFalse("repeat call must not run action when already installed", second);
        assertEquals(1, calls.get());
        assertTrue("still installed after no-op repeat", InstallGuard.isInstalled(HOOK_A, loader));
    }

    @Test
    public void isInstalledFalseAfterRollback() {
        ClassLoader loader = newLoader();
        assertFalse(InstallGuard.isInstalled(HOOK_A, loader));

        try {
            InstallGuard.installOncePerLoader(HOOK_A, loader, () -> {
                throw new RuntimeException("fail");
            });
            fail("expected exception");
        } catch (RuntimeException ignored) {
            // expected
        } catch (Throwable t) {
            fail("unexpected: " + t);
        }
        assertFalse("flag must be cleared after rollback", InstallGuard.isInstalled(HOOK_A, loader));
    }

    @Test
    public void isInstalledReturnsFalseForNullLoader() {
        assertFalse(InstallGuard.isInstalled(HOOK_A, null));
    }

    @Test
    public void isInstalledRejectsInvalidHookId() {
        ClassLoader loader = newLoader();
        try {
            InstallGuard.isInstalled(null, loader);
            fail("expected IllegalArgumentException for null hookId");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            InstallGuard.isInstalled("", loader);
            fail("expected IllegalArgumentException for empty hookId");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void resetForTestingClearsAllHooks() throws Throwable {
        ClassLoader loader = newLoader();
        InstallGuard.installOncePerLoader(HOOK_A, loader, () -> { });
        InstallGuard.installOncePerLoader(HOOK_B, loader, () -> { });
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader));
        assertTrue(InstallGuard.isInstalled(HOOK_B, loader));

        InstallGuard.resetForTesting();

        assertFalse(InstallGuard.isInstalled(HOOK_A, loader));
        assertFalse(InstallGuard.isInstalled(HOOK_B, loader));
    }

    @Test
    public void resetForTestingHookIdClearsOnlyOneHook() throws Throwable {
        ClassLoader loader = newLoader();
        InstallGuard.installOncePerLoader(HOOK_A, loader, () -> { });
        InstallGuard.installOncePerLoader(HOOK_B, loader, () -> { });
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader));
        assertTrue(InstallGuard.isInstalled(HOOK_B, loader));

        InstallGuard.resetForTesting(HOOK_A);

        assertFalse(InstallGuard.isInstalled(HOOK_A, loader));
        assertTrue(InstallGuard.isInstalled(HOOK_B, loader));
    }

    @Test
    public void concurrentCallsRunActionExactlyOnce() throws Exception {
        final int threadCount = 64;
        final ClassLoader loader = newLoader();
        final AtomicInteger actionRuns = new AtomicInteger();
        final AtomicInteger trueReturns = new AtomicInteger();
        final AtomicInteger falseReturns = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>(threadCount);
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        boolean ran = InstallGuard.installOncePerLoader(HOOK_A, loader, () -> {
                            actionRuns.incrementAndGet();
                            // A tiny pause forces overlap between the marking and other threads' fast-path checks.
                            Thread.sleep(2);
                            return;
                        });
                        if (ran) {
                            trueReturns.incrementAndGet();
                        } else {
                            falseReturns.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }));
            }
            start.countDown();
            assertTrue("threads must all finish within 10s", done.await(10, TimeUnit.SECONDS));
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals("action must run exactly once", 1, actionRuns.get());
        assertEquals("only one caller should observe true", 1, trueReturns.get());
        assertEquals("all other callers should observe false", threadCount - 1, falseReturns.get());
        assertEquals("no errors expected", 0, errors.get());
        assertTrue(InstallGuard.isInstalled(HOOK_A, loader));
    }

    @Test
    public void independentLoaderObjectsAreNotEqualByDefault() {
        ClassLoader a = newLoader();
        ClassLoader b = newLoader();
        assertTrue("URLClassLoader instances should be distinct objects", a != b);
        assertSame(a, a);
    }

    @Test
    public void concurrentCallsWithFailingActionRetryConsistently() throws Exception {
        // Regression for the "transient TRUE during failing action" race. With the install lock
        // held across action.run(), N threads serialize through the critical section. The first
        // thread runs the failing action and propagates the throwable; the second thread observes
        // the rolled-back state, runs the (now-succeeding) action and returns true; remaining
        // threads observe TRUE and return false. The test enforces the core invariants:
        //   - exactly one throw escapes the install (the action throws on the first run only)
        //   - trueCount + falseCount + thrownCount == N
        //   - isInstalled <=> someone returned true
        //   - net action.run() invocations == thrownCount + trueCount
        final String hookId = "fail-retry";
        final ClassLoader loader = newLoader();
        final AtomicInteger runCount = new AtomicInteger();
        final InstallGuard.ThrowingRunnable action = () -> {
            int n = runCount.incrementAndGet();
            if (n == 1) {
                throw new RuntimeException("first attempt fails");
            }
        };

        final int N = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(N);
        final AtomicInteger trueCount = new AtomicInteger();
        final AtomicInteger falseCount = new AtomicInteger();
        final AtomicInteger thrownCount = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(N);
        try {
            for (int i = 0; i < N; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        try {
                            boolean r = InstallGuard.installOncePerLoader(hookId, loader, action);
                            if (r) {
                                trueCount.incrementAndGet();
                            } else {
                                falseCount.incrementAndGet();
                            }
                        } catch (Throwable t) {
                            thrownCount.incrementAndGet();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue("threads must all finish within 10s", done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        // Core invariants that must hold for any correct serialization.
        assertEquals("exactly one action invocation should propagate a throwable",
                1, thrownCount.get());
        assertEquals("every thread must record exactly one outcome",
                N, trueCount.get() + falseCount.get() + thrownCount.get());
        assertEquals("net action.run() invocations equals thrownCount + trueCount",
                thrownCount.get() + trueCount.get(), runCount.get());
        assertTrue("at most one thread should observe true",
                trueCount.get() <= 1);
        if (trueCount.get() == 1) {
            assertTrue("install should be present when a thread succeeded",
                    InstallGuard.isInstalled(hookId, loader));
        } else {
            assertFalse("install must be absent when no thread succeeded",
                    InstallGuard.isInstalled(hookId, loader));
        }

        InstallGuard.resetForTesting();
    }

    private static ClassLoader newLoader() {
        return new URLClassLoader(new URL[0], InstallGuardTest.class.getClassLoader());
    }
}
