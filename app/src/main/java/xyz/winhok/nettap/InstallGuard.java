package xyz.winhok.nettap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class InstallGuard {

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    // All access (read and write) MUST be guarded by synchronized(INSTALLED).
    // Inner maps are themselves Collections.synchronizedMap; access them via loadersFor()
    // and use the inner map's monitor for compound operations.
    private static final Map<String, Map<ClassLoader, Boolean>> INSTALLED = new HashMap<>();

    private InstallGuard() {
    }

    public static boolean installOncePerLoader(String hookId, ClassLoader loader, ThrowingRunnable action) throws Throwable {
        requireValidHookId(hookId);
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (loader == null) {
            return false;
        }

        Map<ClassLoader, Boolean> loaders = loadersFor(hookId);
        // Hold the inner map's monitor across the entire install (mark + action + rollback).
        // This serializes installs per hookId. It guarantees that any other thread either:
        //   (a) sees TRUE only after a successful action.run() and returns false, or
        //   (b) sees no entry (because we rolled back) and gets a chance to retry the action.
        // It eliminates the race where a thread observed a transient TRUE that was about to
        // be rolled back by a failing action on another thread.
        synchronized (loaders) {
            if (Boolean.TRUE.equals(loaders.get(loader))) {
                return false;
            }
            loaders.put(loader, Boolean.TRUE);
            boolean success = false;
            try {
                action.run();
                success = true;
                return true;
            } finally {
                if (!success) {
                    loaders.remove(loader);
                }
            }
        }
    }

    public static boolean isInstalled(String hookId, ClassLoader loader) {
        requireValidHookId(hookId);
        if (loader == null) {
            return false;
        }
        Map<ClassLoader, Boolean> loaders;
        synchronized (INSTALLED) {
            loaders = INSTALLED.get(hookId);
        }
        if (loaders == null) {
            return false;
        }
        synchronized (loaders) {
            return Boolean.TRUE.equals(loaders.get(loader));
        }
    }

    public static void resetForTesting() {
        synchronized (INSTALLED) {
            INSTALLED.clear();
        }
    }

    public static void resetForTesting(String hookId) {
        requireValidHookId(hookId);
        synchronized (INSTALLED) {
            INSTALLED.remove(hookId);
        }
    }

    private static Map<ClassLoader, Boolean> loadersFor(String hookId) {
        synchronized (INSTALLED) {
            Map<ClassLoader, Boolean> loaders = INSTALLED.get(hookId);
            if (loaders == null) {
                loaders = Collections.synchronizedMap(new WeakHashMap<ClassLoader, Boolean>());
                INSTALLED.put(hookId, loaders);
            }
            return loaders;
        }
    }

    private static void requireValidHookId(String hookId) {
        if (hookId == null || hookId.isEmpty()) {
            throw new IllegalArgumentException("hookId must not be null or empty");
        }
    }
}
