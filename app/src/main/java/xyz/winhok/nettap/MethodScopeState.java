package xyz.winhok.nettap;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Carries per-method-call state between Xposed before/after hooks and
 * automatically computes elapsed duration on {@link #end(Scope)}.
 *
 * <p>State is stashed inside an opaque {@link Scope} adapter rather than
 * directly on {@code XC_MethodHook.MethodHookParam} so the class has no
 * compile-time dependency on the Xposed API and can be exercised by plain
 * JVM unit tests.</p>
 *
 * <p>Each instance owns one extras key. Production hook classes typically
 * keep a single static instance per hook site. The instance itself holds
 * no mutable shared state, so it is safe to share across threads;
 * per-call data lives entirely inside the {@link Scope}.</p>
 */
public final class MethodScopeState<S> {

    /**
     * Adapter for any "method scope" capable of carrying per-call key/value
     * extras.
     *
     * <ul>
     *     <li>Production: a thin wrapper over
     *         {@code XC_MethodHook.MethodHookParam} (declared in hook
     *         classes so this file stays free of Xposed imports).</li>
     *     <li>Test: an in-memory {@code Map}-backed fake.</li>
     * </ul>
     */
    public interface Scope {
        Object getExtra(String key);

        void setExtra(String key, Object value);
    }

    /**
     * Result returned by {@link #end(Scope)}. When the scope was never
     * started, {@link #getState()} is {@code null} and both timing
     * accessors return {@code 0}.
     */
    public static final class Snapshot<S> {
        private final S state;
        private final long startedNanos;
        private final long durationMs;

        Snapshot(S state, long startedNanos, long durationMs) {
            this.state = state;
            this.startedNanos = startedNanos;
            this.durationMs = durationMs;
        }

        public S getState() {
            return state;
        }

        public long getStartedNanos() {
            return startedNanos;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    private final String key;
    private final Supplier<S> stateFactory;

    public MethodScopeState(String key, Supplier<S> stateFactory) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must be non-null and non-empty");
        }
        if (stateFactory == null) {
            throw new IllegalArgumentException("stateFactory must be non-null");
        }
        this.key = key;
        this.stateFactory = stateFactory;
    }

    /**
     * Records the start time and lazily creates state via the factory. If
     * the scope has already been started, returns the existing state and
     * does not re-invoke the factory.
     */
    public S start(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        Holder<S> existing = holder(scope);
        if (existing != null) {
            return existing.state;
        }
        S state = stateFactory.get();
        scope.setExtra(key, new Holder<>(this, state, System.nanoTime()));
        return state;
    }

    /**
     * Records the start time using a caller-supplied initial state. The
     * factory is not consulted. If the scope has already been started, the
     * existing state wins (idempotent like {@link #start(Scope)}).
     */
    public S start(Scope scope, S initialState) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(initialState, "initialState");
        Holder<S> existing = holder(scope);
        if (existing != null) {
            return existing.state;
        }
        scope.setExtra(key, new Holder<>(this, initialState, System.nanoTime()));
        return initialState;
    }

    /**
     * Returns the current state for this scope, or {@code null} if
     * {@link #start(Scope)} was never called.
     */
    public S get(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        Holder<S> holder = holder(scope);
        return holder == null ? null : holder.state;
    }

    /**
     * Computes elapsed time and returns a snapshot bundling state plus
     * timing. Safe to call without a prior {@link #start(Scope)}; the
     * resulting snapshot will have {@code state == null} and
     * {@code durationMs == 0}.
     */
    public Snapshot<S> end(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        Holder<S> holder = holder(scope);
        if (holder == null) {
            return new Snapshot<>(null, 0L, 0L);
        }
        long elapsedNanos = System.nanoTime() - holder.startedNanos;
        long durationMs = elapsedNanos <= 0L ? 0L : elapsedNanos / 1_000_000L;
        return new Snapshot<>(holder.state, holder.startedNanos, durationMs);
    }

    /** Returns whether {@link #start(Scope)} has been recorded for this scope. */
    public boolean isStarted(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        return holder(scope) != null;
    }

    @SuppressWarnings("unchecked")
    private Holder<S> holder(Scope scope) {
        Object value = scope.getExtra(key);
        if (value instanceof Holder && ((Holder<?>) value).owner == this) {
            return (Holder<S>) value;
        }
        return null;
    }

    private static final class Holder<S> {
        final MethodScopeState<?> owner;
        final S state;
        final long startedNanos;

        Holder(MethodScopeState<?> owner, S state, long startedNanos) {
            this.owner = owner;
            this.state = state;
            this.startedNanos = startedNanos;
        }
    }
}
