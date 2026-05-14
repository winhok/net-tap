package de.robv.android.xposed;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class XSharedPreferences {
    private static final Map<String, Object> VALUES = new HashMap<>();
    private static int reloadCount;

    public XSharedPreferences(String packageName, String prefFileName) {
    }

    public void reload() {
        reloadCount++;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = VALUES.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public int getInt(String key, int fallback) {
        Object value = VALUES.get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    public String getString(String key, String fallback) {
        Object value = VALUES.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> fallback) {
        Object value = VALUES.get(key);
        return value instanceof Set ? (Set<String>) value : fallback;
    }

    public static void putBoolean(String key, boolean value) {
        VALUES.put(key, value);
    }

    public static void putInt(String key, int value) {
        VALUES.put(key, value);
    }

    public static void putString(String key, String value) {
        VALUES.put(key, value);
    }

    public static void putStringSet(String key, Set<String> value) {
        VALUES.put(key, value == null ? Collections.emptySet() : value);
    }

    public static int reloadCount() {
        return reloadCount;
    }

    public static void reset() {
        VALUES.clear();
        reloadCount = 0;
    }
}
