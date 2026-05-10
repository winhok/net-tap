package xyz.winhok.nettap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

final class CronetInstallRegistry {

    /** Installs hooks on a single Cronet class; append each returned unhook into {@code collector} for rollback. */
    @FunctionalInterface
    interface HookFactory {
        void install(Class<?> cls, List<XC_MethodHook.Unhook> collector) throws Throwable;
    }

    private final Map<ClassLoader, Set<String>> installedLoaders = Collections.synchronizedMap(
            new WeakHashMap<ClassLoader, Set<String>>()
    );

    /**
     * Install {@code factory} against each candidate class exactly once per
     * {@code (classLoader, className)} pair. On any throw from {@code factory}
     * the partially-installed hooks are unhooked in reverse order and the
     * registry marker is cleared so a later attempt can retry.
     *
     * @param layer       metrics layer to increment on success; skipped when {@code null}
     * @param packageName host package name, forwarded to {@link MetricsReporter}
     * @return {@code true} iff at least one candidate is now (or was already) installed
     */
    boolean installOnce(
            ClassLoader classLoader,
            List<Class<?>> candidates,
            String layer,
            String packageName,
            HookFactory factory
    ) {
        if (classLoader == null || candidates == null || factory == null) {
            return false;
        }
        int hooked = 0;
        for (Class<?> cls : candidates) {
            if (cls == null) {
                continue;
            }
            String className = cls.getName();
            synchronized (this) {
                if (isInstalled(classLoader, className)) {
                    hooked++;
                    continue;
                }
                List<XC_MethodHook.Unhook> installed = new ArrayList<>();
                try {
                    factory.install(cls, installed);
                    markInstalled(classLoader, className);
                    NetTap.getXposedLogger().log("installed hook: %s", className);
                    if (layer != null) {
                        try {
                            MetricsReporter.incInstalled(packageName, layer);
                        } catch (Throwable ignored) {
                        }
                    }
                    hooked++;
                } catch (Throwable e) {
                    for (int i = installed.size() - 1; i >= 0; i--) {
                        XC_MethodHook.Unhook hook = installed.get(i);
                        if (hook == null) {
                            continue;
                        }
                        try {
                            hook.unhook();
                        } catch (Throwable ignored) {
                        }
                    }
                    unmarkInstalled(classLoader, className);
                    NetTap.getXposedLogger().log(
                            "failed to install %s hook: %s", className, e);
                }
            }
        }
        return hooked > 0;
    }

    /** Append {@code unhooks} into {@code target}, tolerating null inputs. */
    static void addAll(List<XC_MethodHook.Unhook> target, Set<XC_MethodHook.Unhook> unhooks) {
        if (target != null && unhooks != null) {
            target.addAll(unhooks);
        }
    }

    boolean markInstalled(ClassLoader classLoader, String className) {
        if (classLoader == null || className == null || className.length() == 0) {
            return false;
        }
        synchronized (installedLoaders) {
            Set<String> installed = installedLoaders.get(classLoader);
            if (installed == null) {
                installed = new HashSet<>();
                installedLoaders.put(classLoader, installed);
            }
            return installed.add(className);
        }
    }

    void unmarkInstalled(ClassLoader classLoader, String className) {
        if (classLoader == null || className == null) {
            return;
        }
        synchronized (installedLoaders) {
            Set<String> installed = installedLoaders.get(classLoader);
            if (installed == null) {
                return;
            }
            installed.remove(className);
            if (installed.isEmpty()) {
                installedLoaders.remove(classLoader);
            }
        }
    }

    boolean isInstalled(ClassLoader classLoader, String className) {
        if (classLoader == null || className == null) {
            return false;
        }
        synchronized (installedLoaders) {
            Set<String> installed = installedLoaders.get(classLoader);
            return installed != null && installed.contains(className);
        }
    }
}
