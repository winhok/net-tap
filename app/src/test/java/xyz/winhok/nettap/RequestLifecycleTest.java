package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RequestLifecycleTest {

    private static final class TestState {
        String url;
        int code;
        long startedNanos;
    }

    private static RequestLifecycle<Object, TestState> newLifecycle(AtomicInteger factoryCalls) {
        return new RequestLifecycle<>(() -> {
            if (factoryCalls != null) {
                factoryCalls.incrementAndGet();
            }
            return new TestState();
        });
    }

    private static RequestLifecycle<Object, TestState> newLifecycle() {
        return newLifecycle(null);
    }

    @Test
    public void startCreatesFreshStateAndCallsFactoryOnce() {
        AtomicInteger calls = new AtomicInteger();
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle(calls);
        Object key = new Object();

        TestState state = lifecycle.start(key);

        assertNotNull(state);
        assertEquals(1, calls.get());
        assertEquals(1, lifecycle.size());
    }

    @Test
    public void startReturnsExistingStateOnRepeatCall() {
        AtomicInteger calls = new AtomicInteger();
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle(calls);
        Object key = new Object();

        TestState first = lifecycle.start(key);
        TestState second = lifecycle.start(key);

        assertSame(first, second);
        assertEquals(1, calls.get());
        assertEquals(1, lifecycle.size());
    }

    @Test
    public void startWithInitialStateUsesProvidedInstanceWithoutFactory() {
        AtomicInteger calls = new AtomicInteger();
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle(calls);
        Object key = new Object();
        TestState seed = new TestState();
        seed.url = "https://example.com/";
        seed.startedNanos = 42L;

        TestState state = lifecycle.start(key, seed);

        assertSame(seed, state);
        assertSame(seed, lifecycle.get(key));
        assertEquals("https://example.com/", lifecycle.get(key).url);
        assertEquals(0, calls.get());
    }

    @Test
    public void startWithInitialStateDoesNotOverwriteExistingState() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();

        TestState first = lifecycle.start(key);
        TestState seed = new TestState();
        seed.url = "https://other.example.com/";
        TestState second = lifecycle.start(key, seed);

        assertSame(first, second);
    }

    @Test
    public void getReturnsRegisteredState() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        TestState state = lifecycle.start(key);

        assertSame(state, lifecycle.get(key));
    }

    @Test
    public void getReturnsNullForUnknownKey() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();

        assertNull(lifecycle.get(new Object()));
    }

    @Test
    public void updateAppliesMutatorAndReturnsState() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        lifecycle.start(key);

        TestState updated = lifecycle.update(key, s -> {
            s.url = "https://api.example.com/";
            s.code = 200;
        });

        assertNotNull(updated);
        assertEquals("https://api.example.com/", updated.url);
        assertEquals(200, updated.code);
        assertSame(updated, lifecycle.get(key));
    }

    @Test
    public void updateForUnknownKeyReturnsNullAndSkipsMutator() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        AtomicInteger invocations = new AtomicInteger();

        TestState result = lifecycle.update(new Object(), s -> invocations.incrementAndGet());

        assertNull(result);
        assertEquals(0, invocations.get());
    }

    @Test
    public void finishOnceFirstCallReturnsTrueAndRunsFinisher() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        lifecycle.start(key);
        AtomicInteger invocations = new AtomicInteger();

        boolean result = lifecycle.finishOnce(key, s -> {
            invocations.incrementAndGet();
            s.code = 999;
        });

        assertTrue(result);
        assertEquals(1, invocations.get());
        assertEquals(999, lifecycle.get(key).code);
    }

    @Test
    public void finishOnceSecondCallReturnsFalseAndSkipsFinisher() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        lifecycle.start(key);
        lifecycle.finishOnce(key, s -> { /* first finish */ });

        AtomicInteger invocations = new AtomicInteger();
        boolean result = lifecycle.finishOnce(key, s -> invocations.incrementAndGet());

        assertFalse(result);
        assertEquals(0, invocations.get());
    }

    @Test
    public void finishOnceForUnknownKeyReturnsFalse() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        AtomicInteger invocations = new AtomicInteger();

        boolean result = lifecycle.finishOnce(new Object(), s -> invocations.incrementAndGet());

        assertFalse(result);
        assertEquals(0, invocations.get());
    }

    @Test
    public void removeClearsEntryAndSubsequentGetReturnsNull() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        TestState state = lifecycle.start(key);

        TestState removed = lifecycle.remove(key);

        assertSame(state, removed);
        assertNull(lifecycle.get(key));
        assertEquals(0, lifecycle.size());
    }

    @Test
    public void concurrentFinishOnceInvokesFinisherExactlyOnce() throws InterruptedException {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        final int threadCount = 64;
        final Object key = new Object();
        lifecycle.start(key);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger trueCount = new AtomicInteger();
            AtomicInteger finisherInvocations = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        boolean won = lifecycle.finishOnce(key,
                                s -> finisherInvocations.incrementAndGet());
                        if (won) {
                            trueCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));

            assertEquals(1, trueCount.get());
            assertEquals(1, finisherInvocations.get());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    public void finishOnceDoesNotConsumeGateWhenFinisherThrows() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        lifecycle.start(key);

        IllegalStateException thrown = null;
        try {
            lifecycle.finishOnce(key, s -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException e) {
            thrown = e;
        }
        assertNotNull(thrown);
        assertEquals("boom", thrown.getMessage());

        AtomicInteger invocations = new AtomicInteger();
        boolean retried = lifecycle.finishOnce(key, s -> invocations.incrementAndGet());

        assertTrue(retried);
        assertEquals(1, invocations.get());
    }

    @Test
    public void updatePartialMutationRemainsVisibleAfterMutatorThrows() {
        RequestLifecycle<Object, TestState> lifecycle = newLifecycle();
        Object key = new Object();
        TestState seed = new TestState();
        seed.url = "before";
        seed.code = 100;
        lifecycle.start(key, seed);

        IllegalStateException thrown = null;
        try {
            lifecycle.update(key, s -> {
                s.url = "after";
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException e) {
            thrown = e;
        }
        assertNotNull(thrown);
        assertEquals("after", lifecycle.get(key).url);
        assertEquals(100, lifecycle.get(key).code);
    }
}
