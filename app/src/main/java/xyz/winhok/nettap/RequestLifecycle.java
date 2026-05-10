package xyz.winhok.nettap;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Generic per-request lifecycle state machine for HTTP-style hooks where a
 * single logical request is observed across multiple hook callbacks
 * (constructor, intermediate updates, terminal callback).
 *
 * <p>Designed for stacks like Cronet, HttpURLConnection, and WebSocket where
 * the framework "request" object identity (e.g. {@code CronetUrlRequest}) must
 * not be strongly retained by the logger; otherwise the logger would defeat GC
 * of the underlying request.
 *
 * <h2>Threading</h2>
 * <ul>
 *   <li>Compound map operations (e.g. {@code start} computeIfAbsent) run under
 *       the {@code synchronizedMap} monitor.</li>
 *   <li>Per-request state mutations and the once-only finish gate run under
 *       {@code synchronized(state)} so {@link #update(Object, Consumer)} and
 *       {@link #finishOnce(Object, Consumer)} cannot interleave for the same
 *       key.</li>
 * </ul>
 *
 * <h2>Memory</h2>
 * The key map is a {@link WeakHashMap}: when the hook key {@code K} becomes
 * unreachable, its lifecycle entry and finished-flag entry are reclaimed by
 * GC. The user's state type {@code S} is not required to implement any
 * interface; the "already finished" flag is tracked internally in a parallel
 * {@code WeakHashMap} keyed by the same {@code K}, so the flag lifetime is
 * tied to the hook object — not to {@code S} — and never leaks.
 *
 * @param <K> hook object type used as identity key (e.g. the framework
 *            request object). Held weakly.
 * @param <S> caller-defined state type. No interface required.
 */
public final class RequestLifecycle<K, S> {
    private final Supplier<S> stateFactory;
    private final Map<K, S> states =
            Collections.synchronizedMap(new WeakHashMap<K, S>());
    // Logically a Set of finished keys; Boolean.TRUE values are presence sentinels.
    // Backed by Map<K, Boolean> with WeakHashMap keys to mirror the lifecycle of
    // the primary `states` map without independently leaking K references.
    private final Map<K, Boolean> finishedFlags =
            Collections.synchronizedMap(new WeakHashMap<K, Boolean>());

    public RequestLifecycle(Supplier<S> stateFactory) {
        if (stateFactory == null) {
            throw new NullPointerException("stateFactory");
        }
        this.stateFactory = stateFactory;
    }

    /**
     * Register a fresh state for {@code key} using the supplied factory, or
     * return the already-registered state if {@code start} was previously
     * called (computeIfAbsent semantics — never overwrites).
     */
    public S start(K key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        synchronized (states) {
            S existing = states.get(key);
            if (existing != null) {
                return existing;
            }
            S fresh = stateFactory.get();
            if (fresh == null) {
                throw new IllegalStateException("stateFactory returned null");
            }
            states.put(key, fresh);
            return fresh;
        }
    }

    /**
     * Like {@link #start(Object)} but uses an explicit initial state instead
     * of invoking the factory. If a state already exists for {@code key} the
     * existing state is returned and {@code initialState} is discarded.
     */
    public S start(K key, S initialState) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (initialState == null) {
            throw new NullPointerException("initialState");
        }
        synchronized (states) {
            S existing = states.get(key);
            if (existing != null) {
                return existing;
            }
            states.put(key, initialState);
            return initialState;
        }
    }

    /**
     * Like {@link #start(Object)} but uses a caller-supplied factory invoked
     * only when no state is registered for {@code key}. Use when the state
     * requires call-site-local data (e.g. packageName) the constructor-level
     * factory does not have, and the caller is on a hot path where eagerly
     * allocating an initial state per call would be wasteful.
     */
    public S start(K key, Supplier<S> factory) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        synchronized (states) {
            S existing = states.get(key);
            if (existing != null) {
                return existing;
            }
            S fresh = factory.get();
            if (fresh == null) {
                throw new IllegalStateException("factory returned null");
            }
            states.put(key, fresh);
            return fresh;
        }
    }

    /**
     * @return the registered state, or {@code null} if {@code key} was never
     *         started or has already been collected/removed.
     */
    public S get(K key) {
        if (key == null) {
            return null;
        }
        return states.get(key);
    }

    /**
     * Atomically run {@code mutator} against the registered state under that
     * state's monitor.
     *
     * <p><b>Exception safety:</b> {@code mutator} is executed inside the state's
     * monitor without rollback. If {@code mutator} throws after partially
     * modifying the state, those mutations remain visible to subsequent
     * {@link #get}/{@link #update} callers. Callers must guarantee mutator
     * idempotency or apply rollback themselves.
     *
     * @return the (now-mutated) state, or {@code null} if {@code key} was
     *         never started; {@code mutator} is not invoked in that case.
     */
    public S update(K key, Consumer<S> mutator) {
        if (key == null || mutator == null) {
            return null;
        }
        S state = states.get(key);
        if (state == null) {
            return null;
        }
        synchronized (state) {
            mutator.accept(state);
        }
        return state;
    }

    /**
     * Run {@code finisher} exactly once for the given key. Subsequent calls
     * return {@code false} without invoking {@code finisher}, even when
     * multiple threads race.
     *
     * <p>{@code finisher.accept(state)} runs under {@code synchronized(state)}
     * to coordinate with {@link #update(Object, Consumer)}.
     *
     * <p>Does <strong>not</strong> remove the entry on completion: GC reclaims
     * it once the hook key {@code K} becomes unreachable. This avoids racing
     * eviction with downstream callers that may still legitimately call
     * {@link #get(Object)} after the terminal callback fires.
     *
     * <p>If {@code finisher} throws, the once-only gate is <em>not</em> consumed;
     * the exception propagates and subsequent calls may retry. Callers should
     * provide an idempotent finisher.
     *
     * @return {@code true} on the first successful invocation, {@code false}
     *         on subsequent calls or when no state is registered.
     */
    public boolean finishOnce(K key, Consumer<S> finisher) {
        if (key == null || finisher == null) {
            return false;
        }
        S state = states.get(key);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (Boolean.TRUE.equals(finishedFlags.get(key))) {
                return false;
            }
            finisher.accept(state);
            finishedFlags.put(key, Boolean.TRUE);
        }
        return true;
    }

    /**
     * Manually drop the entry for {@code key}. Usually unnecessary — the
     * {@link WeakHashMap} reclaims entries automatically once {@code key} is
     * unreachable. Provided for tests and explicit-cleanup scenarios.
     *
     * @return the previously registered state, or {@code null} if none.
     */
    public S remove(K key) {
        if (key == null) {
            return null;
        }
        finishedFlags.remove(key);
        return states.remove(key);
    }

    /**
     * @return the current number of live entries.
     *         Expunges stale entries before counting; intended for diagnostics and tests.
     */
    public int size() {
        return states.size();
    }
}
