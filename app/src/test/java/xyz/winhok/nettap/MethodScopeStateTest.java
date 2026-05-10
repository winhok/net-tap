package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MethodScopeStateTest {

    private static final String KEY = "nettap.test_state";

    private static final class FakeScope implements MethodScopeState.Scope {
        private final HashMap<String, Object> extras = new HashMap<>();

        @Override
        public Object getExtra(String key) {
            return extras.get(key);
        }

        @Override
        public void setExtra(String key, Object value) {
            extras.put(key, value);
        }
    }

    private static final class State {
        final int id;

        State(int id) {
            this.id = id;
        }
    }

    @Test
    public void startInFreshScopeInvokesFactoryOnceAndStoresState() {
        AtomicInteger factoryCalls = new AtomicInteger();
        MethodScopeState<State> handle = new MethodScopeState<>(
                KEY,
                () -> new State(factoryCalls.incrementAndGet()));

        FakeScope scope = new FakeScope();
        State first = handle.start(scope);

        assertNotNull(first);
        assertEquals(1, factoryCalls.get());
        assertEquals(1, first.id);
        assertTrue(handle.isStarted(scope));
    }

    @Test
    public void startOnAlreadyStartedScopeReturnsExistingStateWithoutCallingFactory() {
        AtomicInteger factoryCalls = new AtomicInteger();
        MethodScopeState<State> handle = new MethodScopeState<>(
                KEY,
                () -> new State(factoryCalls.incrementAndGet()));

        FakeScope scope = new FakeScope();
        State first = handle.start(scope);
        State second = handle.start(scope);

        assertSame(first, second);
        assertEquals(1, factoryCalls.get());
    }

    @Test
    public void startWithInitialStateBypassesFactory() {
        AtomicInteger factoryCalls = new AtomicInteger();
        MethodScopeState<State> handle = new MethodScopeState<>(
                KEY,
                () -> {
                    factoryCalls.incrementAndGet();
                    return new State(-1);
                });

        FakeScope scope = new FakeScope();
        State seed = new State(42);
        State stored = handle.start(scope, seed);

        assertSame(seed, stored);
        assertEquals(0, factoryCalls.get());
        assertSame(seed, handle.get(scope));
    }

    @Test
    public void startWithInitialStateOnAlreadyStartedScopeKeepsExisting() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(0));
        FakeScope scope = new FakeScope();

        State first = handle.start(scope, new State(1));
        State second = handle.start(scope, new State(2));

        assertSame(first, second);
        assertEquals(1, first.id);
    }

    @Test
    public void getReturnsStoredStateWhenStarted() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(7));
        FakeScope scope = new FakeScope();
        State started = handle.start(scope);
        assertSame(started, handle.get(scope));
    }

    @Test
    public void getReturnsNullWhenNotStarted() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(7));
        FakeScope scope = new FakeScope();
        assertNull(handle.get(scope));
    }

    @Test
    public void endAfterStartProvidesOriginalStateAndNonNegativeDuration() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(99));
        FakeScope scope = new FakeScope();
        State started = handle.start(scope);

        MethodScopeState.Snapshot<State> snapshot = handle.end(scope);

        assertNotNull(snapshot);
        assertSame(started, snapshot.getState());
        assertTrue("startedNanos should be recorded", snapshot.getStartedNanos() != 0L);
        assertTrue("durationMs should be >= 0 but was " + snapshot.getDurationMs(), snapshot.getDurationMs() >= 0L);
    }

    @Test
    public void endWithoutStartProducesEmptySnapshot() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(0));
        FakeScope scope = new FakeScope();

        MethodScopeState.Snapshot<State> snapshot = handle.end(scope);

        assertNotNull(snapshot);
        assertNull(snapshot.getState());
        assertEquals(0L, snapshot.getStartedNanos());
        assertEquals(0L, snapshot.getDurationMs());
    }

    @Test
    public void isStartedTracksScopeLifecycle() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(0));
        FakeScope scope = new FakeScope();

        assertFalse(handle.isStarted(scope));
        handle.start(scope);
        assertTrue(handle.isStarted(scope));
    }

    @Test
    public void distinctKeysOnSameScopeDoNotInterfere() {
        MethodScopeState<State> a = new MethodScopeState<>("k.a", () -> new State(1));
        MethodScopeState<State> b = new MethodScopeState<>("k.b", () -> new State(2));
        FakeScope scope = new FakeScope();

        State sa = a.start(scope);
        assertFalse("b should be independent of a", b.isStarted(scope));

        State sb = b.start(scope);
        assertSame(sa, a.get(scope));
        assertSame(sb, b.get(scope));
        assertEquals(1, sa.id);
        assertEquals(2, sb.id);
    }

    @Test
    public void constructorRejectsNullKey() {
        try {
            new MethodScopeState<State>(null, () -> new State(0));
            fail("expected IllegalArgumentException for null key");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void constructorRejectsEmptyKey() {
        try {
            new MethodScopeState<State>("", () -> new State(0));
            fail("expected IllegalArgumentException for empty key");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void constructorRejectsNullFactory() {
        try {
            new MethodScopeState<State>(KEY, null);
            fail("expected IllegalArgumentException for null factory");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void durationReflectsElapsedTime() throws InterruptedException {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(0));
        FakeScope scope = new FakeScope();
        handle.start(scope);

        Thread.sleep(20);

        MethodScopeState.Snapshot<State> snapshot = handle.end(scope);
        assertTrue("durationMs " + snapshot.getDurationMs() + " should be >= 15", snapshot.getDurationMs() >= 15L);
        assertTrue("durationMs " + snapshot.getDurationMs() + " should be < 5000", snapshot.getDurationMs() < 5000L);
    }

    @Test
    public void startWithNullInitialStateThrowsNpe() {
        MethodScopeState<State> handle = new MethodScopeState<>(KEY, () -> new State(0));
        FakeScope scope = new FakeScope();
        try {
            handle.start(scope, null);
            fail("expected NullPointerException for null initialState");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void differentMethodScopeStatesWithSameKeyDoNotCrossContaminate() {
        FakeScope scope = new FakeScope();
        MethodScopeState<String> a = new MethodScopeState<>("k", () -> "a-state");
        MethodScopeState<Integer> b = new MethodScopeState<>("k", () -> 42);

        a.start(scope);
        assertEquals("a-state", a.get(scope));
        // b uses the same key but a different owner: must not see a's holder
        // and must not throw ClassCastException.
        assertNull("b should not see a's holder", b.get(scope));
        assertFalse("b should report not-started before its own start", b.isStarted(scope));

        // b.start overwrites the slot; a's holder is lost - acceptable.
        Integer bState = b.start(scope);
        assertEquals(Integer.valueOf(42), bState);
        assertNull("a should now miss because b overwrote", a.get(scope));
        assertFalse("a should report not-started after b overwrites", a.isStarted(scope));
        assertEquals(Integer.valueOf(42), b.get(scope));
    }
}
