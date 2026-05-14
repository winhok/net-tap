package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class XposedBridge {
    public static final ClassLoader BOOTCLASSLOADER = null;
    public static int XPOSED_BRIDGE_VERSION = 82;

    private static final List<HookRecord> HOOKS = new ArrayList<>();
    private static final List<String> LOGS = new ArrayList<>();

    private XposedBridge() {
    }

    public static synchronized void log(String message) {
        LOGS.add(message);
    }

    public static synchronized void log(Throwable throwable) {
        LOGS.add(String.valueOf(throwable));
    }

    public static synchronized XC_MethodHook.Unhook hookMethod(
            Member member, XC_MethodHook callback) {
        HOOKS.add(new HookRecord(member, callback));
        return null;
    }

    public static synchronized void unhookMethod(Member member, XC_MethodHook callback) {
        HOOKS.removeIf(record -> record.member.equals(member) && record.callback == callback);
    }

    public static synchronized Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> clazz, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new LinkedHashSet<>();
        Class<?> current = clazz;
        while (current != null) {
            for (java.lang.reflect.Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    hookMethod(method, callback);
                }
            }
            current = current.getSuperclass();
        }
        return unhooks;
    }

    public static synchronized Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> clazz, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new LinkedHashSet<>();
        for (java.lang.reflect.Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            hookMethod(constructor, callback);
        }
        return unhooks;
    }

    public static synchronized List<HookRecord> hookedMethods() {
        return Collections.unmodifiableList(new ArrayList<>(HOOKS));
    }

    public static synchronized List<String> logs() {
        return Collections.unmodifiableList(new ArrayList<>(LOGS));
    }

    public static synchronized void resetForTesting() {
        HOOKS.clear();
        LOGS.clear();
    }

    public static final class HookRecord {
        public final Member member;
        public final XC_MethodHook callback;

        private HookRecord(Member member, XC_MethodHook callback) {
            this.member = member;
            this.callback = callback;
        }
    }
}
