package xyz.winhok.nettap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class CronetInstallRegistry {
    private final Map<ClassLoader, Set<String>> installedLoaders = Collections.synchronizedMap(
            new WeakHashMap<ClassLoader, Set<String>>()
    );

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
